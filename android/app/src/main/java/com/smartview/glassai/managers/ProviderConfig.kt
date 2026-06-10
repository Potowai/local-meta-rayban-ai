package com.smartview.glassai.managers

/**
 * A snapshot of all the parameters needed to make one vision API call.
 *
 * Created from the live state in [APIProviderManager] + [APIKeyManager].
 * The point: the network layer can build a request from this struct
 * without ever touching SharedPreferences or StateFlows, which makes
 * the primary/fallback orchestration trivial.
 */
data class ProviderConfig(
    val provider: APIProvider,
    val baseURL: String,
    val model: String,
    val apiKey: String,
    val alibabaEndpoint: AlibabaEndpoint? = null
) {
    val isCloud: Boolean
        get() = provider == APIProvider.ALIBABA || provider == APIProvider.OPENROUTER

    val requiresApiKey: Boolean
        get() = isCloud

    /** Human-readable label for logs and UI. */
    val displayName: String
        get() = when (provider) {
            APIProvider.ALIBABA -> "Alibaba Dashscope" +
                (alibabaEndpoint?.let { " (${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }})" } ?: "")
            APIProvider.OPENROUTER -> "OpenRouter"
            APIProvider.CUSTOM -> "Local Server"
        }
}
