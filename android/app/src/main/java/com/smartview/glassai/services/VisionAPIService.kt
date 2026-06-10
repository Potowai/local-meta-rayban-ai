package com.smartview.glassai.services

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smartview.glassai.managers.AlibabaEndpoint
import com.smartview.glassai.managers.APIProvider
import com.smartview.glassai.managers.APIProviderManager
import com.smartview.glassai.managers.ProviderConfig
import com.smartview.glassai.managers.QuickVisionModeManager
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
 * Vision API Service
 * Supports multiple providers: Alibaba Cloud Dashscope (Beijing/Singapore), OpenRouter
 * 1:1 port from iOS VisionAPIConfig + QuickVisionService
 */
class VisionAPIService(
    private val apiKeyManager: APIKeyManager,
    private val providerManager: APIProviderManager,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "VisionAPIService"

        // Provider-specific URLs
        const val ALIBABA_BEIJING_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        const val ALIBABA_SINGAPORE_URL = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
        const val OPENROUTER_URL = "https://openrouter.ai/api/v1"

        // Default Models
        const val DEFAULT_ALIBABA_MODEL = "qwen-vl-plus"
        const val DEFAULT_OPENROUTER_MODEL = "google/gemini-3-flash-preview"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // MARK: - Configuration

    private val currentProvider: APIProvider
        get() = providerManager.currentProvider.value

    private val alibabaEndpoint: AlibabaEndpoint
        get() = providerManager.alibabaEndpoint.value

    private val baseURL: String
        get() = when (currentProvider) {
            APIProvider.ALIBABA -> when (alibabaEndpoint) {
                AlibabaEndpoint.BEIJING -> ALIBABA_BEIJING_URL
                AlibabaEndpoint.SINGAPORE -> ALIBABA_SINGAPORE_URL
            }
            APIProvider.OPENROUTER -> OPENROUTER_URL
            APIProvider.CUSTOM -> providerManager.customBaseURL.value
        }

    private val apiKey: String?
        get() = when (currentProvider) {
            APIProvider.ALIBABA -> apiKeyManager.getAPIKey(APIProvider.ALIBABA, alibabaEndpoint)
            APIProvider.OPENROUTER -> apiKeyManager.getAPIKey(APIProvider.OPENROUTER)
            APIProvider.CUSTOM -> {
                val k = apiKeyManager.getCustomAPIKey()
                if (k.isNullOrBlank()) null else k
            }
        }

    private val model: String
        get() = when (currentProvider) {
            APIProvider.CUSTOM -> providerManager.customModel.value
            else -> providerManager.selectedModel.value
        }

    // MARK: - Analyze Image

    suspend fun analyzeImage(image: Bitmap, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey
            if (key.isNullOrBlank() && currentProvider != APIProvider.CUSTOM) {
                return@withContext Result.failure(VisionAPIError.NoAPIKey)
            }

            Log.d(TAG, "Analyzing image with provider: $currentProvider, model: $model")

            val base64Image = encodeImageToBase64(image)
            val requestBody = buildRequestBody(base64Image, prompt)
            val url = "$baseURL/chat/completions"

            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")

            // Authorization header is optional for local servers
            if (!key.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $key")
            }

            // Add OpenRouter-specific headers
            if (currentProvider == APIProvider.OPENROUTER) {
                requestBuilder.addHeader("HTTP-Referer", "https://localmeta.app")
                requestBuilder.addHeader("X-Title", "LocalMeta")
            }

            val request = requestBuilder
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code} - $responseBody")
                return@withContext Result.failure(VisionAPIError.APIError("API Error: ${response.code} - $responseBody"))
            }

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(VisionAPIError.EmptyResponse)
            }

            val result = parseResponse(responseBody)
            if (result.isNullOrEmpty()) {
                return@withContext Result.failure(VisionAPIError.InvalidResponse)
            }

            Log.d(TAG, "Analysis successful")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image: ${e.message}")
            Result.failure(e)
        }
    }

    // MARK: - Analyze Image (with primary → fallback routing)

    /**
     * Analyze an image, trying the primary provider first and falling back to
     * a secondary one if the primary call hits a network error or HTTP 5xx.
     *
     * 4xx errors (auth, bad request) are **not** retried — they indicate a
     * user-side config problem (wrong API key, invalid model name) and
     * falling back would mask it. The result surfaces the error immediately.
     *
     * @param primary   The provider to try first. Required.
     * @param fallback  The provider to try on retryable failure. Nullable.
     * @return A [VisionResult] describing the outcome.
     */
    suspend fun analyzeWithFallback(
        image: Bitmap,
        prompt: String,
        primary: ProviderConfig,
        fallback: ProviderConfig?
    ): VisionResult = withContext(Dispatchers.IO) {
        if (primary.requiresApiKey && primary.apiKey.isBlank()) {
            // Surface the missing-key error directly. The user needs to fix
            // their config; falling back wouldn't help.
            return@withContext VisionResult.Failure(
                message = "Missing API key for ${primary.displayName}",
                attempted = listOf(primary),
                usedFallback = false
            )
        }

        val primaryResult = runOne(image, prompt, primary)
        if (primaryResult is VisionResult.Success) {
            return@withContext primaryResult
        }
        if (primaryResult is VisionResult.Failure) {
            val shouldRetry = primaryResult.retryable && fallback != null && fallback != primary
            if (!shouldRetry) {
                return@withContext primaryResult
            }
            Log.w(TAG, "Primary ${primary.displayName} failed (retryable). Trying fallback ${fallback!!.displayName}")
            val fallbackResult = runOne(image, prompt, fallback)
            return@withContext when (fallbackResult) {
                is VisionResult.Success -> fallbackResult.copy(usedFallback = true)
                is VisionResult.Failure -> VisionResult.Failure(
                    message = "Primary (${primary.displayName}) and fallback (${fallback!!.displayName}) both failed: ${fallbackResult.message}",
                    attempted = listOf(primary, fallback!!),
                    usedFallback = false
                )
            }
        }
        // Should be unreachable.
        VisionResult.Failure("Unknown state", listOf(primary), usedFallback = false)
    }

    /**
     * One-shot call to a single provider. Captures whether the failure was
     * retryable (network/5xx) so the orchestrator can decide.
     */
    private suspend fun runOne(
        image: Bitmap,
        prompt: String,
        config: ProviderConfig
    ): VisionResult {
        return try {
            val base64Image = encodeImageToBase64(image)
            val requestBody = buildRequestBodyFor(config, base64Image, prompt)
            val url = "${config.baseURL.trimEnd('/')}/chat/completions"

            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")

            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
            }
            if (config.provider == APIProvider.OPENROUTER) {
                requestBuilder.addHeader("HTTP-Referer", "https://localmeta.app")
                requestBuilder.addHeader("X-Title", "LocalMeta")
            }

            val request = requestBuilder
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Vision request → ${config.displayName} (${config.model}) at $url")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                val code = response.code
                val body = responseBody?.take(200) ?: ""
                val retryable = code >= 500
                Log.e(TAG, "${config.displayName} returned HTTP $code: $body")
                return VisionResult.Failure(
                    message = "HTTP $code from ${config.displayName}: $body",
                    attempted = listOf(config),
                    usedFallback = false,
                    retryable = retryable
                )
            }

            if (responseBody.isNullOrEmpty()) {
                return VisionResult.Failure(
                    "Empty response from ${config.displayName}",
                    listOf(config),
                    usedFallback = false,
                    retryable = false
                )
            }

            val result = parseResponse(responseBody)
            if (result.isNullOrEmpty()) {
                return VisionResult.Failure(
                    "Could not parse response from ${config.displayName}",
                    listOf(config),
                    usedFallback = false,
                    retryable = false
                )
            }

            VisionResult.Success(text = result, usedFallback = false)
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Timeout calling ${config.displayName}: ${e.message}")
            VisionResult.Failure("Timeout: ${config.displayName}", listOf(config), usedFallback = false, retryable = true)
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Unknown host calling ${config.displayName}: ${e.message}")
            VisionResult.Failure("Unknown host: ${config.displayName}", listOf(config), usedFallback = false, retryable = true)
        } catch (e: IOException) {
            Log.w(TAG, "IO error calling ${config.displayName}: ${e.message}")
            VisionResult.Failure("Network error: ${config.displayName}", listOf(config), usedFallback = false, retryable = true)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error calling ${config.displayName}: ${e.message}")
            VisionResult.Failure("Error: ${e.message ?: e::class.simpleName}", listOf(config), usedFallback = false, retryable = false)
        }
    }

    // MARK: - Quick Vision (for background recognition)
    // Uses QuickVisionModeManager to get the prompt based on selected mode

    suspend fun quickVision(image: Bitmap, language: String = "zh-CN"): Result<String> {
        // Use mode manager if context is available, otherwise fall back to language-based prompt
        val prompt = context?.let {
            val modeManager = QuickVisionModeManager.getInstance(it)
            val currentMode = modeManager.currentMode.value
            val modePrompt = modeManager.getPrompt()
            Log.d(TAG, "QuickVision using mode: ${currentMode.id}, prompt length: ${modePrompt.length}")
            Log.d(TAG, "QuickVision prompt: ${modePrompt.take(100)}...")
            modePrompt
        } ?: getQuickVisionPrompt(language)

        return analyzeImage(image, prompt)
    }

    /**
     * Get localized Quick Vision prompt matching iOS implementation
     */
    private fun getQuickVisionPrompt(language: String): String {
        return when (language) {
            "zh-CN" -> """
                你是一个智能眼镜AI助手。请用简洁的中文描述图片内容，适合语音播报。

                要求：
                1. 用1-2句话描述主要内容
                2. 语言自然、口语化
                3. 不要使用标点符号过多
                4. 总字数控制在50字以内
                5. 直接描述，不要说"图片中"或"我看到"
            """.trimIndent()
            "en-US" -> """
                You are a smart glasses AI assistant. Please describe the image content concisely, suitable for voice announcement.

                Requirements:
                1. Describe the main content in 1-2 sentences
                2. Use natural, conversational language
                3. Don't use too many punctuation marks
                4. Keep the total under 50 words
                5. Describe directly, don't say "in the image" or "I see"
            """.trimIndent()
            "ja-JP" -> """
                あなたはスマートグラスのAIアシスタントです。画像の内容を簡潔に日本語で説明してください。音声読み上げに適した形式で。

                要件：
                1. 1-2文で主要な内容を説明
                2. 自然で会話的な言葉を使用
                3. 句読点を多用しない
                4. 合計50文字以内
                5. 直接説明し、「画像には」や「見えます」とは言わない
            """.trimIndent()
            "ko-KR" -> """
                당신은 스마트 안경 AI 어시스턴트입니다. 이미지 내용을 간결한 한국어로 설명해 주세요. 음성 안내에 적합하게.

                요구사항:
                1. 1-2문장으로 주요 내용 설명
                2. 자연스럽고 대화체로
                3. 구두점을 많이 사용하지 않음
                4. 총 50자 이내
                5. 직접 설명하고, "이미지에는"이나 "보입니다"라고 말하지 않음
            """.trimIndent()
            else -> getQuickVisionPrompt("en-US")
        }
    }

    // MARK: - Private Helpers

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun buildRequestBody(base64Image: String, prompt: String): String {
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
                        "text" to prompt
                    )
                )
            )
        )

        val request = mapOf(
            "model" to model,
            "messages" to messages,
            "max_tokens" to 2000
        )

        return gson.toJson(request)
    }

    /**
     * Build a request body for a specific [ProviderConfig]. Used by the
     * primary/fallback routing so each call carries the right model name.
     */
    private fun buildRequestBodyFor(
        config: ProviderConfig,
        base64Image: String,
        prompt: String
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
                        "text" to prompt
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

    private fun parseResponse(responseBody: String): String? {
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                message?.get("content")?.asString
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}")
            null
        }
    }
}

sealed class VisionAPIError : Exception() {
    object InvalidImage : VisionAPIError()
    object EmptyResponse : VisionAPIError()
    object InvalidResponse : VisionAPIError()
    object NoAPIKey : VisionAPIError()
    data class APIError(override val message: String) : VisionAPIError()
}

/**
 * Result of [VisionAPIService.analyzeWithFallback].
 *
 * - [Success.text] is the model's response.
 * - [Success.usedFallback] is `true` if the primary call failed and the
 *   fallback succeeded.
 * - [Failure] carries the error message, the configs that were attempted,
 *   and whether the failure was retryable (so the caller can decide to
 *   retry with a different fallback if available).
 */
sealed class VisionResult {
    data class Success(
        val text: String,
        val usedFallback: Boolean
    ) : VisionResult()

    data class Failure(
        val message: String,
        val attempted: List<com.smartview.glassai.managers.ProviderConfig>,
        val usedFallback: Boolean,
        val retryable: Boolean = false
    ) : VisionResult()
}
