package com.smartview.glassai.services

import android.graphics.Bitmap
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smartview.glassai.managers.APIProvider
import com.smartview.glassai.managers.APIProviderManager
import com.smartview.glassai.managers.ProviderConfig
import com.smartview.glassai.models.FoodItem
import com.smartview.glassai.models.FoodNutritionResponse
import com.smartview.glassai.utils.APIKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 食物营养分析AI服务
 * Supports all vision providers including local OpenAI-compatible servers
 */
class LeanEatService(
    private val apiKey: String,
    private val providerManager: APIProviderManager? = null
) {

    companion object {
        private const val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        private const val MODEL = "qwen-vl-plus"

        private val NUTRITION_PROMPT = """
请分析这张图片中的食物，并以JSON格式返回营养分析结果。

请严格按照以下JSON格式返回（不要包含任何其他文字）：
{
  "foods": [
    {
      "name": "食物名称",
      "portion": "份量描述",
      "calories": 热量数值(整数),
      "protein": 蛋白质克数(小数),
      "fat": 脂肪克数(小数),
      "carbs": 碳水化合物克数(小数),
      "fiber": 膳食纤维(小数或null),
      "sugar": 糖克数(小数或null),
      "healthRating": "优秀/良好/一般/较差"
    }
  ],
  "totalCalories": 总热量(整数),
  "totalProtein": 总蛋白质(小数),
  "totalFat": 总脂肪(小数),
  "totalCarbs": 总碳水(小数),
  "healthScore": 0-100的健康评分(整数),
  "suggestions": ["建议1", "建议2", "建议3"]
}

健康评分标准：
- 80-100: 优秀（低脂、高蛋白、富含纤维）
- 60-79: 良好（营养较均衡）
- 40-59: 一般（可能高脂或高糖）
- 0-39: 较差（高热量、低营养）

请只返回JSON，不要有任何其他解释文字。
""".trimIndent()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Effective URL & model — fall back to the historical Alibaba defaults when
    // no providerManager is supplied (legacy callers passing only an API key).
    private val effectiveBaseURL: String
        get() {
            val pm = providerManager ?: return BASE_URL.substringBefore("/chat/completions")
            return when (pm.currentProvider.value) {
                APIProvider.CUSTOM -> pm.customBaseURL.value
                else -> pm.currentBaseURL
            }
        }

    private val effectiveModel: String
        get() = providerManager?.currentModel ?: MODEL

    suspend fun analyzeFood(image: Bitmap): Result<FoodNutritionResponse> = withContext(Dispatchers.IO) {
        try {
            val base64Image = encodeImageToBase64(image)
            val requestBody = buildRequestBody(base64Image)
            val url = "$effectiveBaseURL/chat/completions"

            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")

            // Authorization is optional for local servers (e.g. Ollama)
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val request = requestBuilder
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API Error: ${response.code} - $responseBody"))
            }

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response from API"))
            }

            val nutritionResponse = parseNutritionResponse(responseBody)
            if (nutritionResponse == null) {
                return@withContext Result.failure(Exception("Failed to parse nutrition data"))
            }

            Result.success(nutritionResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // MARK: - Primary → fallback routing (parallel to VisionAPIService)

    /**
     * Same primary/fallback contract as [VisionAPIService.analyzeWithFallback].
     * 4xx is surfaced, 5xx and network errors retry against the fallback.
     */
    suspend fun analyzeFoodWithFallback(
        image: Bitmap,
        primary: ProviderConfig,
        fallback: ProviderConfig?
    ): LeanEatResult = withContext(Dispatchers.IO) {
        if (primary.requiresApiKey && primary.apiKey.isBlank()) {
            return@withContext LeanEatResult.Failure(
                "Missing API key for ${primary.displayName}",
                listOf(primary),
                usedFallback = false
            )
        }

        val primaryResult = runOne(image, primary)
        if (primaryResult is LeanEatResult.Success) {
            return@withContext primaryResult
        }
        if (primaryResult is LeanEatResult.Failure) {
            val shouldRetry = primaryResult.retryable && fallback != null && fallback != primary
            if (!shouldRetry) {
                return@withContext primaryResult
            }
            val fallbackResult = runOne(image, fallback!!)
            return@withContext when (fallbackResult) {
                is LeanEatResult.Success -> fallbackResult.copy(usedFallback = true)
                is LeanEatResult.Failure -> LeanEatResult.Failure(
                    "Primary (${primary.displayName}) and fallback (${fallback!!.displayName}) both failed: ${fallbackResult.message}",
                    listOf(primary, fallback!!),
                    usedFallback = false
                )
            }
        }
        LeanEatResult.Failure("Unknown state", listOf(primary), usedFallback = false)
    }

    private suspend fun runOne(
        image: Bitmap,
        config: ProviderConfig
    ): LeanEatResult {
        return try {
            val base64Image = encodeImageToBase64(image)
            val requestBody = buildRequestBodyFor(config, base64Image)
            val url = "${config.baseURL.trimEnd('/')}/chat/completions"

            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")

            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
            }

            val request = requestBuilder
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                val code = response.code
                val body = responseBody?.take(200) ?: ""
                val retryable = code >= 500
                return LeanEatResult.Failure(
                    "HTTP $code from ${config.displayName}: $body",
                    listOf(config),
                    usedFallback = false,
                    retryable = retryable
                )
            }
            if (responseBody.isNullOrEmpty()) {
                return LeanEatResult.Failure("Empty response from ${config.displayName}", listOf(config), usedFallback = false)
            }
            val parsed = parseNutritionResponse(responseBody)
            if (parsed == null) {
                return LeanEatResult.Failure("Could not parse nutrition response from ${config.displayName}", listOf(config), usedFallback = false)
            }
            LeanEatResult.Success(parsed, usedFallback = false)
        } catch (e: SocketTimeoutException) {
            LeanEatResult.Failure("Timeout: ${config.displayName}", listOf(config), usedFallback = false, retryable = true)
        } catch (e: UnknownHostException) {
            LeanEatResult.Failure("Unknown host: ${config.displayName}", listOf(config), usedFallback = false, retryable = true)
        } catch (e: IOException) {
            LeanEatResult.Failure("Network error: ${config.displayName}", listOf(config), usedFallback = false, retryable = true)
        } catch (e: Exception) {
            LeanEatResult.Failure("Error: ${e.message ?: e::class.simpleName}", listOf(config), usedFallback = false)
        }
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun buildRequestBody(base64Image: String): String {
        val messages = listOf(
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:image/jpeg;base64,$base64Image"
                        )
                    ),
                    mapOf(
                        "type" to "text",
                        "text" to NUTRITION_PROMPT
                    )
                )
            )
        )

        val request = mapOf(
            "model" to effectiveModel,
            "messages" to messages,
            "max_tokens" to 2000
        )

        return gson.toJson(request)
    }

    private fun buildRequestBodyFor(
        config: ProviderConfig,
        base64Image: String
    ): String {
        val messages = listOf(
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:image/jpeg;base64,$base64Image"
                        )
                    ),
                    mapOf(
                        "type" to "text",
                        "text" to NUTRITION_PROMPT
                    )
                )
            )
        )

        val request = mapOf(
            "model" to config.model,
            "messages" to messages,
            "max_tokens" to 2000
        )

        return gson.toJson(request)
    }

    private fun parseNutritionResponse(responseBody: String): FoodNutritionResponse? {
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) return null

            val message = choices[0].asJsonObject.getAsJsonObject("message")
            val content = message?.get("content")?.asString ?: return null

            // Extract JSON from content (in case it has extra text)
            val jsonContent = extractJson(content) ?: return null

            // Parse the nutrition JSON
            parseNutritionJson(jsonContent)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractJson(content: String): String? {
        // Find JSON object in content
        val startIndex = content.indexOf('{')
        val endIndex = content.lastIndexOf('}')
        return if (startIndex >= 0 && endIndex > startIndex) {
            content.substring(startIndex, endIndex + 1)
        } else {
            null
        }
    }

    private fun parseNutritionJson(json: String): FoodNutritionResponse? {
        return try {
            val jsonObject = gson.fromJson(json, JsonObject::class.java)

            val foodsArray = jsonObject.getAsJsonArray("foods")
            val foods = mutableListOf<FoodItem>()

            foodsArray?.forEach { element ->
                val food = element.asJsonObject
                foods.add(
                    FoodItem(
                        name = food.get("name")?.asString ?: "",
                        portion = food.get("portion")?.asString ?: "",
                        calories = food.get("calories")?.asInt ?: 0,
                        protein = food.get("protein")?.asDouble ?: 0.0,
                        fat = food.get("fat")?.asDouble ?: 0.0,
                        carbs = food.get("carbs")?.asDouble ?: 0.0,
                        fiber = food.get("fiber")?.asDouble,
                        sugar = food.get("sugar")?.asDouble,
                        healthRating = food.get("healthRating")?.asString ?: "良好"
                    )
                )
            }

            val suggestionsArray = jsonObject.getAsJsonArray("suggestions")
            val suggestions = suggestionsArray?.map { it.asString } ?: emptyList()

            FoodNutritionResponse(
                foods = foods,
                totalCalories = jsonObject.get("totalCalories")?.asInt ?: 0,
                totalProtein = jsonObject.get("totalProtein")?.asDouble ?: 0.0,
                totalFat = jsonObject.get("totalFat")?.asDouble ?: 0.0,
                totalCarbs = jsonObject.get("totalCarbs")?.asDouble ?: 0.0,
                healthScore = jsonObject.get("healthScore")?.asInt ?: 50,
                suggestions = suggestions
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * Result of [LeanEatService.analyzeFoodWithFallback]. Same contract as
 * [VisionResult] from [VisionAPIService].
 */
sealed class LeanEatResult {
    data class Success(
        val response: FoodNutritionResponse,
        val usedFallback: Boolean
    ) : LeanEatResult()

    data class Failure(
        val message: String,
        val attempted: List<ProviderConfig>,
        val usedFallback: Boolean,
        val retryable: Boolean = false
    ) : LeanEatResult()
}
