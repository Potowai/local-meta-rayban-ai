package com.smartview.glassai.managers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Quick health check for the configured AI provider.
 *
 * Pings the provider endpoint (GET /v1/models for local / custom servers,
 * or the provider's health endpoint for cloud) and returns a status summary.
 */
sealed class ProviderStatus {
    data class Online(
        val providerName: String,
        val model: String,
        val serverVersion: String? = null,
        val latencyMs: Long
    ) : ProviderStatus()

    data class Offline(
        val providerName: String,
        val reason: String
    ) : ProviderStatus()

    object NotConfigured : ProviderStatus()
}

object ProviderStatusChecker {

    private const val TAG = "ProviderStatus"
    private const val TIMEOUT_MS = 5000

    /**
     * Checks the currently active primary provider. Returns as fast as
     * possible — 5s timeout, single GET call.
     */
    suspend fun checkPrimary(
        providerManager: APIProviderManager,
        apiKeyManager: com.smartview.glassai.utils.APIKeyManager
    ): ProviderStatus = withContext(Dispatchers.IO) {
        val config = providerManager.currentPrimaryConfig(apiKeyManager)
            ?: return@withContext ProviderStatus.NotConfigured
        checkConfig(config)
    }

    /**
     * Checks the fallback (local server) provider.
     */
    suspend fun checkFallback(
        providerManager: APIProviderManager,
        apiKeyManager: com.smartview.glassai.utils.APIKeyManager
    ): ProviderStatus = withContext(Dispatchers.IO) {
        if (!providerManager.fallbackEnabled.value) {
            return@withContext ProviderStatus.NotConfigured
        }
        val config = providerManager.currentFallbackConfig(apiKeyManager)
            ?: return@withContext ProviderStatus.NotConfigured
        checkConfig(config)
    }

    private suspend fun checkConfig(config: ProviderConfig): ProviderStatus {
        return try {
            val start = System.currentTimeMillis()
            val baseUrl = config.baseURL.trimEnd('/')
            val url = URL("$baseUrl/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false

            val responseCode = conn.responseCode
            val latency = System.currentTimeMillis() - start

            if (responseCode != 200) {
                return ProviderStatus.Offline(
                    providerName = config.displayName,
                    reason = "HTTP $responseCode"
                )
            }

            val body = conn.inputStream.bufferedReader().readText().take(2000)
            val (version, modelName) = parseModelResponse(body, config.model)

            ProviderStatus.Online(
                providerName = config.displayName,
                model = modelName ?: config.model,
                serverVersion = version,
                latencyMs = latency
            )
        } catch (e: java.net.ConnectException) {
            ProviderStatus.Offline(config.displayName, "Unreachable")
        } catch (e: java.net.SocketTimeoutException) {
            ProviderStatus.Offline(config.displayName, "Timeout")
        } catch (e: Exception) {
            Log.w(TAG, "Status check failed", e)
            ProviderStatus.Offline(config.displayName, e.message ?: "Error")
        }
    }

    private fun parseModelResponse(body: String, requestedModel: String): Pair<String?, String?> {
        return try {
            val json = JSONObject(body)
            val data = json.optJSONArray("data")
            var version: String? = null
            var foundModel: String? = null
            if (data != null) {
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id == requestedModel || requestedModel.isEmpty() || foundModel == null) {
                        foundModel = id
                    }
                }
            }
            // Try to get version from Ollama-specific fields
            version = json.optString("version", null) ?: json.optString("server_version", null)
            Pair(version, foundModel)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }
}
