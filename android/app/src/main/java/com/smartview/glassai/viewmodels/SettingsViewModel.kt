package com.smartview.glassai.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartview.glassai.data.ConversationStorage
import com.smartview.glassai.managers.AlibabaEndpoint
import com.smartview.glassai.managers.AlibabaVisionModel
import com.smartview.glassai.managers.APIProvider
import com.smartview.glassai.managers.APIProviderManager
import com.smartview.glassai.managers.AppLanguage
import com.smartview.glassai.managers.LanguageManager
import com.smartview.glassai.managers.LiveAIProvider
import com.smartview.glassai.managers.LocalServerPreset
import com.smartview.glassai.managers.OpenRouterModel
import com.smartview.glassai.utils.AIModel
import com.smartview.glassai.utils.APIKeyManager
import com.smartview.glassai.utils.OutputLanguage
import com.smartview.glassai.utils.StreamQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SettingsViewModel
 * Supports multi-provider configuration (Alibaba/OpenRouter, Alibaba/Google for Live AI)
 * 1:1 port from iOS settings structure
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyManager = APIKeyManager.getInstance(application)
    private val providerManager = APIProviderManager.getInstance(application)
    private val conversationStorage = ConversationStorage.getInstance(application)

    // Vision API Provider
    private val _visionProvider = MutableStateFlow(providerManager.currentProvider.value)
    val visionProvider: StateFlow<APIProvider> = _visionProvider.asStateFlow()

    // Alibaba Endpoint
    private val _alibabaEndpoint = MutableStateFlow(providerManager.alibabaEndpoint.value)
    val alibabaEndpoint: StateFlow<AlibabaEndpoint> = _alibabaEndpoint.asStateFlow()

    // Live AI Provider
    private val _liveAIProvider = MutableStateFlow(providerManager.liveAIProvider.value)
    val liveAIProvider: StateFlow<LiveAIProvider> = _liveAIProvider.asStateFlow()

    // API Keys status
    private val _hasAlibabaBeijingKey = MutableStateFlow(apiKeyManager.hasAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.BEIJING))
    val hasAlibabaBeijingKey: StateFlow<Boolean> = _hasAlibabaBeijingKey.asStateFlow()

    private val _hasAlibabaSingaporeKey = MutableStateFlow(apiKeyManager.hasAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.SINGAPORE))
    val hasAlibabaSingaporeKey: StateFlow<Boolean> = _hasAlibabaSingaporeKey.asStateFlow()

    private val _hasOpenRouterKey = MutableStateFlow(apiKeyManager.hasAPIKey(APIProvider.OPENROUTER))
    val hasOpenRouterKey: StateFlow<Boolean> = _hasOpenRouterKey.asStateFlow()

    private val _hasGoogleKey = MutableStateFlow(apiKeyManager.hasGoogleAPIKey())
    val hasGoogleKey: StateFlow<Boolean> = _hasGoogleKey.asStateFlow()

    // Legacy hasApiKey for backward compatibility
    private val _hasApiKey = MutableStateFlow(apiKeyManager.hasAPIKey())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _apiKeyMasked = MutableStateFlow(getMaskedApiKey())
    val apiKeyMasked: StateFlow<String> = _apiKeyMasked.asStateFlow()

    // AI Model (for Live AI)
    private val _selectedModel = MutableStateFlow(providerManager.liveAIModel.value)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Vision Model
    private val _selectedVisionModel = MutableStateFlow(providerManager.selectedModel.value)
    val selectedVisionModel: StateFlow<String> = _selectedVisionModel.asStateFlow()

    // Output Language
    private val _selectedLanguage = MutableStateFlow(apiKeyManager.getOutputLanguage())
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Video Quality
    private val _selectedQuality = MutableStateFlow(apiKeyManager.getVideoQuality())
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    // Conversation count
    private val _conversationCount = MutableStateFlow(conversationStorage.getConversationCount())
    val conversationCount: StateFlow<Int> = _conversationCount.asStateFlow()

    // Error/Success messages
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // Dialog states
    private val _showApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = _showApiKeyDialog.asStateFlow()

    private val _showModelDialog = MutableStateFlow(false)
    val showModelDialog: StateFlow<Boolean> = _showModelDialog.asStateFlow()

    private val _showLanguageDialog = MutableStateFlow(false)
    val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

    private val _showQualityDialog = MutableStateFlow(false)
    val showQualityDialog: StateFlow<Boolean> = _showQualityDialog.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _showVisionProviderDialog = MutableStateFlow(false)
    val showVisionProviderDialog: StateFlow<Boolean> = _showVisionProviderDialog.asStateFlow()

    private val _showEndpointDialog = MutableStateFlow(false)
    val showEndpointDialog: StateFlow<Boolean> = _showEndpointDialog.asStateFlow()

    private val _showLiveAIProviderDialog = MutableStateFlow(false)
    val showLiveAIProviderDialog: StateFlow<Boolean> = _showLiveAIProviderDialog.asStateFlow()

    // App Language
    private val _appLanguage = MutableStateFlow(LanguageManager.getCurrentLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _showAppLanguageDialog = MutableStateFlow(false)
    val showAppLanguageDialog: StateFlow<Boolean> = _showAppLanguageDialog.asStateFlow()

    private val _showVisionModelDialog = MutableStateFlow(false)
    val showVisionModelDialog: StateFlow<Boolean> = _showVisionModelDialog.asStateFlow()

    // Vision Model selection - expose provider manager states
    val openRouterModels: StateFlow<List<OpenRouterModel>> = providerManager.openRouterModels
    val isLoadingModels: StateFlow<Boolean> = providerManager.isLoadingModels
    val modelsError: StateFlow<String?> = providerManager.modelsError

    // Local / Custom server config
    val customPreset: StateFlow<LocalServerPreset> = providerManager.customPreset
    val customBaseURL: StateFlow<String> = providerManager.customBaseURL
    val customModel: StateFlow<String> = providerManager.customModel

    // Custom server dialog state
    private val _showCustomServerDialog = MutableStateFlow(false)
    val showCustomServerDialog: StateFlow<Boolean> = _showCustomServerDialog.asStateFlow()

    // Current editing key type
    private val _editingKeyType = MutableStateFlow<EditingKeyType?>(null)
    val editingKeyType: StateFlow<EditingKeyType?> = _editingKeyType.asStateFlow()

    enum class EditingKeyType {
        ALIBABA_BEIJING,
        ALIBABA_SINGAPORE,
        OPENROUTER,
        GOOGLE
    }

    init {
        observeProviderChanges()
    }

    private fun observeProviderChanges() {
        viewModelScope.launch {
            providerManager.currentProvider.collect { provider ->
                _visionProvider.value = provider
                refreshApiKeyStatus()
            }
        }
        viewModelScope.launch {
            providerManager.alibabaEndpoint.collect { endpoint ->
                _alibabaEndpoint.value = endpoint
                refreshApiKeyStatus()
            }
        }
        viewModelScope.launch {
            providerManager.liveAIProvider.collect { provider ->
                _liveAIProvider.value = provider
                _selectedModel.value = providerManager.liveAIModel.value
            }
        }
    }

    private fun refreshApiKeyStatus() {
        _hasAlibabaBeijingKey.value = apiKeyManager.hasAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.BEIJING)
        _hasAlibabaSingaporeKey.value = apiKeyManager.hasAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.SINGAPORE)
        _hasOpenRouterKey.value = apiKeyManager.hasAPIKey(APIProvider.OPENROUTER)
        _hasGoogleKey.value = apiKeyManager.hasGoogleAPIKey()
        _hasApiKey.value = apiKeyManager.hasAPIKey()
        _apiKeyMasked.value = getMaskedApiKey()
    }

    // MARK: - Vision Provider

    fun showVisionProviderDialog() {
        _showVisionProviderDialog.value = true
    }

    fun hideVisionProviderDialog() {
        _showVisionProviderDialog.value = false
    }

    fun selectVisionProvider(provider: APIProvider) {
        providerManager.setCurrentProvider(provider)
        _visionProvider.value = provider
        _showVisionProviderDialog.value = false
        _message.value = "Vision API switched to ${provider.displayName}"
        refreshApiKeyStatus()
    }

    // MARK: - Alibaba Endpoint

    fun showEndpointDialog() {
        _showEndpointDialog.value = true
    }

    fun hideEndpointDialog() {
        _showEndpointDialog.value = false
    }

    fun selectEndpoint(endpoint: AlibabaEndpoint) {
        providerManager.setAlibabaEndpoint(endpoint)
        _alibabaEndpoint.value = endpoint
        _showEndpointDialog.value = false
        _message.value = "Endpoint switched to ${endpoint.displayName}"
        refreshApiKeyStatus()
    }

    // MARK: - Live AI Provider

    fun showLiveAIProviderDialog() {
        _showLiveAIProviderDialog.value = true
    }

    fun hideLiveAIProviderDialog() {
        _showLiveAIProviderDialog.value = false
    }

    fun selectLiveAIProvider(provider: LiveAIProvider) {
        providerManager.setLiveAIProvider(provider)
        _liveAIProvider.value = provider
        _selectedModel.value = provider.defaultModel
        _showLiveAIProviderDialog.value = false
        _message.value = "Live AI switched to ${provider.displayName}"
    }

    // MARK: - API Key Management

    fun showApiKeyDialog() {
        _showApiKeyDialog.value = true
    }

    fun hideApiKeyDialog() {
        _showApiKeyDialog.value = false
        _editingKeyType.value = null
    }

    fun showApiKeyDialogForType(type: EditingKeyType) {
        _editingKeyType.value = type
        _showApiKeyDialog.value = true
    }

    fun saveApiKey(apiKey: String): Boolean {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            _message.value = "API Key cannot be empty"
            return false
        }

        val success = when (_editingKeyType.value) {
            EditingKeyType.ALIBABA_BEIJING -> apiKeyManager.saveAPIKey(trimmedKey, APIProvider.ALIBABA, AlibabaEndpoint.BEIJING)
            EditingKeyType.ALIBABA_SINGAPORE -> apiKeyManager.saveAPIKey(trimmedKey, APIProvider.ALIBABA, AlibabaEndpoint.SINGAPORE)
            EditingKeyType.OPENROUTER -> apiKeyManager.saveAPIKey(trimmedKey, APIProvider.OPENROUTER)
            EditingKeyType.GOOGLE -> apiKeyManager.saveGoogleAPIKey(trimmedKey)
            null -> apiKeyManager.saveAPIKey(trimmedKey)
        }

        if (success) {
            refreshApiKeyStatus()
            _message.value = "API Key saved successfully"
            _showApiKeyDialog.value = false
            _editingKeyType.value = null
        } else {
            _message.value = "Failed to save API Key"
        }
        return success
    }

    fun deleteApiKey(): Boolean {
        val success = when (_editingKeyType.value) {
            EditingKeyType.ALIBABA_BEIJING -> apiKeyManager.deleteAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.BEIJING)
            EditingKeyType.ALIBABA_SINGAPORE -> apiKeyManager.deleteAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.SINGAPORE)
            EditingKeyType.OPENROUTER -> apiKeyManager.deleteAPIKey(APIProvider.OPENROUTER)
            EditingKeyType.GOOGLE -> apiKeyManager.deleteGoogleAPIKey()
            null -> apiKeyManager.deleteAPIKey()
        }

        if (success) {
            refreshApiKeyStatus()
            _message.value = "API Key deleted"
        } else {
            _message.value = "Failed to delete API Key"
        }
        return success
    }

    fun getAvailableModels(): List<AIModel> = AIModel.entries

    fun getAvailableLanguages(): List<OutputLanguage> = OutputLanguage.entries

    private fun getMaskedApiKey(): String {
        val apiKey = apiKeyManager.getAPIKey() ?: return ""
        if (apiKey.length <= 8) return "****"
        return "${apiKey.take(4)}****${apiKey.takeLast(4)}"
    }

    fun getMaskedKeyForType(type: EditingKeyType): String {
        val key = when (type) {
            EditingKeyType.ALIBABA_BEIJING -> apiKeyManager.getAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.BEIJING)
            EditingKeyType.ALIBABA_SINGAPORE -> apiKeyManager.getAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.SINGAPORE)
            EditingKeyType.OPENROUTER -> apiKeyManager.getAPIKey(APIProvider.OPENROUTER)
            EditingKeyType.GOOGLE -> apiKeyManager.getGoogleAPIKey()
        } ?: return ""
        if (key.length <= 8) return "****"
        return "${key.take(4)}****${key.takeLast(4)}"
    }

    fun getCurrentKeyForType(type: EditingKeyType): String {
        return when (type) {
            EditingKeyType.ALIBABA_BEIJING -> apiKeyManager.getAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.BEIJING)
            EditingKeyType.ALIBABA_SINGAPORE -> apiKeyManager.getAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.SINGAPORE)
            EditingKeyType.OPENROUTER -> apiKeyManager.getAPIKey(APIProvider.OPENROUTER)
            EditingKeyType.GOOGLE -> apiKeyManager.getGoogleAPIKey()
        } ?: ""
    }

    // AI Model Management
    fun showModelDialog() {
        _showModelDialog.value = true
    }

    fun hideModelDialog() {
        _showModelDialog.value = false
    }

    fun selectModel(model: AIModel) {
        providerManager.setLiveAIModel(model.id)
        _selectedModel.value = model.id
        _showModelDialog.value = false
        _message.value = "Model changed to ${model.displayName}"
    }

    fun getSelectedModelDisplayName(): String {
        val modelId = _selectedModel.value
        return AIModel.entries.find { it.id == modelId }?.displayName ?: modelId
    }

    // Language Management
    fun showLanguageDialog() {
        _showLanguageDialog.value = true
    }

    fun hideLanguageDialog() {
        _showLanguageDialog.value = false
    }

    fun selectLanguage(language: OutputLanguage) {
        apiKeyManager.saveOutputLanguage(language.code)
        _selectedLanguage.value = language.code
        _showLanguageDialog.value = false
        _message.value = "Language changed to ${language.displayName}"
    }

    // App Language Functions
    fun showAppLanguageDialog() {
        _showAppLanguageDialog.value = true
    }

    fun hideAppLanguageDialog() {
        _showAppLanguageDialog.value = false
    }

    fun selectAppLanguage(language: AppLanguage) {
        LanguageManager.setLanguage(getApplication(), language)
        _appLanguage.value = language
        _showAppLanguageDialog.value = false

        // Auto-sync output language with app language
        val outputLangCode = when (language) {
            AppLanguage.CHINESE -> "zh-CN"
            AppLanguage.ENGLISH -> "en-US"
            AppLanguage.SYSTEM -> {
                // Detect system language
                val systemLocale = java.util.Locale.getDefault()
                if (systemLocale.language == "zh") "zh-CN" else "en-US"
            }
        }
        apiKeyManager.saveOutputLanguage(outputLangCode)
        _selectedLanguage.value = outputLangCode

        _message.value = "App language changed to ${language.displayName}"
    }

    fun getAppLanguageDisplayName(): String {
        return when (_appLanguage.value) {
            AppLanguage.SYSTEM -> "跟随系统 / System"
            AppLanguage.CHINESE -> "中文"
            AppLanguage.ENGLISH -> "English"
        }
    }

    fun getAvailableAppLanguages(): List<AppLanguage> = LanguageManager.getAvailableLanguages()

    // Vision Model Functions
    fun showVisionModelDialog() {
        _showVisionModelDialog.value = true
        // Auto-fetch OpenRouter models when dialog opens
        if (_visionProvider.value == APIProvider.OPENROUTER) {
            fetchOpenRouterModels()
        }
    }

    fun hideVisionModelDialog() {
        _showVisionModelDialog.value = false
    }

    fun selectVisionModel(modelId: String) {
        providerManager.setSelectedModel(modelId)
        _selectedVisionModel.value = modelId
        _showVisionModelDialog.value = false
        _message.value = "Model changed to $modelId"
    }

    fun fetchOpenRouterModels() {
        viewModelScope.launch {
            providerManager.fetchOpenRouterModels(apiKeyManager)
        }
    }

    // MARK: - Custom Local Server

    fun showCustomServerDialog() {
        _showCustomServerDialog.value = true
    }

    fun hideCustomServerDialog() {
        _showCustomServerDialog.value = false
    }

    // MARK: - Fallback routing

    /**
     * Pass-through to [APIProviderManager.fallbackEnabled]. The screen binds
     * the toggle directly to this flow.
     */
    val fallbackEnabled: StateFlow<Boolean> get() = providerManager.fallbackEnabled

    fun setFallbackEnabled(enabled: Boolean) {
        providerManager.setFallbackEnabled(enabled)
    }

    /**
     * A one-shot derived flow that says "is the fallback usable right now?".
     * True iff: the toggle is on AND the local server config produces a
     * valid [com.smartview.glassai.managers.ProviderConfig].
     */
    val fallbackReady: StateFlow<Boolean> = providerManager.fallbackEnabled
        .map { enabled ->
            enabled && providerManager.hasUsableFallback(apiKeyManager)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Returns a short human-readable summary of the configured fallback
     * target. E.g. "Ollama — http://localhost:11434/v1 — llava".
     */
    fun fallbackTargetSummary(): String {
        val cfg = providerManager.currentFallbackConfig(apiKeyManager) ?: return "—"
        val presetName = providerManager.customPreset.value.displayName
        return "$presetName — ${cfg.baseURL} — ${cfg.model}"
    }

    fun saveCustomServerConfig(
        preset: LocalServerPreset,
        baseURL: String,
        model: String,
        apiKey: String
    ): Boolean {
        val trimmedURL = baseURL.trim()
        val trimmedModel = model.trim()

        if (trimmedURL.isBlank() || trimmedModel.isBlank()) {
            _message.value = "URL and model name are required"
            return false
        }

        // Normalize URL: strip trailing slash and ensure /v1 suffix
        var normalized = trimmedURL.trimEnd('/')
        if (!normalized.endsWith("/v1")) {
            normalized += "/v1"
        }

        providerManager.setCustomPreset(preset)
        providerManager.setCustomBaseURL(normalized)
        providerManager.setCustomModel(trimmedModel)
        apiKeyManager.saveCustomAPIKey(apiKey)

        // If the user is on the custom provider, update the selected vision model too
        if (providerManager.currentProvider.value == APIProvider.CUSTOM) {
            providerManager.setSelectedModel(trimmedModel)
        }

        refreshApiKeyStatus()
        _message.value = "Local server configured"
        _showCustomServerDialog.value = false
        return true
    }

    /** Quick check that the local server is reachable and lists its models. */
    suspend fun testCustomServerConnection(
        baseURL: String,
        apiKey: String
    ): TestConnectionResult = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            var normalized = baseURL.trim().trimEnd('/')
            if (!normalized.endsWith("/v1")) {
                normalized += "/v1"
            }
            val request = okhttp3.Request.Builder()
                .url("$normalized/models")
                .get()
            if (apiKey.isNotBlank()) {
                request.addHeader("Authorization", "Bearer $apiKey")
            }
            val response = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request.build())
                .execute()
            if (!response.isSuccessful) {
                val body = response.body?.string()?.take(200) ?: ""
                return@withContext TestConnectionResult.Failure("HTTP ${response.code} — $body")
            }
            val body = response.body?.string() ?: return@withContext TestConnectionResult.Success(emptyList())
            try {
                val json = org.json.JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext TestConnectionResult.Success(emptyList())
                val models = (0 until data.length()).mapNotNull { i ->
                    data.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
                }
                TestConnectionResult.Success(models)
            } catch (e: Exception) {
                TestConnectionResult.Failure("Invalid JSON: ${e.message}")
            }
        } catch (e: Exception) {
            TestConnectionResult.Failure(e.message ?: "Connection failed")
        }
    }

    sealed class TestConnectionResult {
        data class Success(val models: List<String>) : TestConnectionResult()
        data class Failure(val message: String) : TestConnectionResult()
    }

    fun searchOpenRouterModels(query: String): List<OpenRouterModel> {
        return providerManager.searchModels(query)
    }

    fun getAlibabaVisionModels(): List<AlibabaVisionModel> {
        return AlibabaVisionModel.availableModels
    }

    fun getSelectedVisionModelDisplayName(): String {
        val modelId = _selectedVisionModel.value
        // Check Alibaba models first
        AlibabaVisionModel.availableModels.find { it.id == modelId }?.let {
            return it.displayName
        }
        // Otherwise return the model ID (for OpenRouter models)
        return modelId
    }

    fun getSelectedLanguageDisplayName(): String {
        val langCode = _selectedLanguage.value
        return OutputLanguage.entries.find { it.code == langCode }?.let {
            "${it.nativeName} (${it.displayName})"
        } ?: langCode
    }

    // Video Quality Management
    fun getAvailableQualities(): List<StreamQuality> = StreamQuality.entries

    fun showQualityDialog() {
        _showQualityDialog.value = true
    }

    fun hideQualityDialog() {
        _showQualityDialog.value = false
    }

    fun selectQuality(quality: StreamQuality) {
        apiKeyManager.saveVideoQuality(quality.id)
        _selectedQuality.value = quality.id
        _showQualityDialog.value = false
        _message.value = "Video quality changed"
    }

    fun getSelectedQuality(): StreamQuality {
        val qualityId = _selectedQuality.value
        return StreamQuality.entries.find { it.id == qualityId } ?: StreamQuality.MEDIUM
    }

    // Conversation Management
    fun showDeleteConfirmDialog() {
        _showDeleteConfirmDialog.value = true
    }

    fun hideDeleteConfirmDialog() {
        _showDeleteConfirmDialog.value = false
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            val success = conversationStorage.deleteAllConversations()
            if (success) {
                _conversationCount.value = 0
                _message.value = "All conversations deleted"
            } else {
                _message.value = "Failed to delete conversations"
            }
            _showDeleteConfirmDialog.value = false
        }
    }

    fun refreshConversationCount() {
        _conversationCount.value = conversationStorage.getConversationCount()
    }

    // Message handling
    fun clearMessage() {
        _message.value = null
    }

    // Get current API key (for editing)
    fun getCurrentApiKey(): String {
        return when (_editingKeyType.value) {
            EditingKeyType.ALIBABA_BEIJING -> apiKeyManager.getAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.BEIJING)
            EditingKeyType.ALIBABA_SINGAPORE -> apiKeyManager.getAPIKey(APIProvider.ALIBABA, AlibabaEndpoint.SINGAPORE)
            EditingKeyType.OPENROUTER -> apiKeyManager.getAPIKey(APIProvider.OPENROUTER)
            EditingKeyType.GOOGLE -> apiKeyManager.getGoogleAPIKey()
            null -> apiKeyManager.getAPIKey()
        } ?: ""
    }
}
