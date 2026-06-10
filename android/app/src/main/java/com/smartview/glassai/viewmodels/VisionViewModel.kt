package com.smartview.glassai.viewmodels

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartview.glassai.managers.APIProviderManager
import com.smartview.glassai.services.VisionAPIService
import com.smartview.glassai.services.VisionResult
import com.smartview.glassai.utils.APIKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class VisionViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyManager = APIKeyManager.getInstance(application)
    private val providerManager = APIProviderManager.getInstance(application)
    private var visionService: VisionAPIService? = null

    // State
    sealed class ViewState {
        object Idle : ViewState()
        object Capturing : ViewState()
        object Analyzing : ViewState()
        data class Result(val description: String) : ViewState()
        data class Error(val message: String) : ViewState()
    }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Idle)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage: StateFlow<Bitmap?> = _capturedImage.asStateFlow()

    private val _analysisResult = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    /**
     * Emits a one-shot signal (e.g. a localized string resource id) when the
     * vision service falls back from the primary provider to the local
     * server. The screen observes this and shows a Toast/Snackbar.
     *
     * Resource ids are int values so the UI layer can resolve them to
     * translated strings without coupling this ViewModel to Android context.
     */
    private val _fallbackNotice = MutableSharedFlow<FallbackNotice>(extraBufferCapacity = 1)
    val fallbackNotice: SharedFlow<FallbackNotice> = _fallbackNotice

    /**
     * Marker type for fallback events. Carries the primary and fallback
     * display names so the UI can build a contextual message.
     */
    data class FallbackNotice(
        val primaryName: String,
        val fallbackName: String
    )

    // Custom prompt for analysis
    private val _customPrompt = MutableStateFlow("")
    val customPrompt: StateFlow<String> = _customPrompt.asStateFlow()

    companion object {
        private val DEFAULT_PROMPTS = listOf(
            "请描述这张图片中你看到的内容，包括主要物体、场景和任何有趣的细节。",
            "What do you see in this image? Describe the main objects, scene, and any interesting details.",
            "请识别图片中的文字内容。",
            "Please identify and read any text visible in this image.",
            "这是什么？请详细说明。",
            "What is this? Please explain in detail."
        )
    }

    init {
        initializeService()
    }

    private fun initializeService() {
        if (providerManager.hasAPIKey(apiKeyManager)) {
            visionService = VisionAPIService(apiKeyManager, providerManager)
        }
    }

    fun setCapturedImage(bitmap: Bitmap) {
        _capturedImage.value = bitmap
        _viewState.value = ViewState.Capturing
        _analysisResult.value = null
    }

    fun setCustomPrompt(prompt: String) {
        _customPrompt.value = prompt
    }

    fun analyzeImage(prompt: String? = null) {
        val image = _capturedImage.value
        if (image == null) {
            _errorMessage.value = "No image captured"
            return
        }

        if (visionService == null) {
            visionService = VisionAPIService(apiKeyManager, providerManager)
        }

        val analysisPrompt = prompt ?: _customPrompt.value.ifBlank { DEFAULT_PROMPTS[0] }

        viewModelScope.launch {
            _viewState.value = ViewState.Analyzing
            _isAnalyzing.value = true

            try {
                val primary = providerManager.currentPrimaryConfig(apiKeyManager)
                val fallback = if (providerManager.fallbackEnabled.value) {
                    providerManager.currentFallbackConfig(apiKeyManager)
                } else null

                val result = visionService!!.analyzeWithFallback(
                    image = image,
                    prompt = analysisPrompt,
                    primary = primary,
                    fallback = fallback
                )

                when (result) {
                    is VisionResult.Success -> {
                        if (result.usedFallback) {
                            _fallbackNotice.tryEmit(
                                FallbackNotice(
                                    primaryName = primary.displayName,
                                    fallbackName = result.attemptedNameOrEmpty(fallback)
                                )
                            )
                        }
                        _analysisResult.value = result.text
                        _viewState.value = ViewState.Result(result.text)
                    }
                    is VisionResult.Failure -> {
                        _errorMessage.value = result.message
                        _viewState.value = ViewState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
                _viewState.value = ViewState.Error(e.message ?: "Analysis failed")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun retakePhoto() {
        _capturedImage.value = null
        _analysisResult.value = null
        _viewState.value = ViewState.Idle
        _errorMessage.value = null
        _customPrompt.value = ""
    }

    fun saveImageToGallery(): Boolean {
        val bitmap = _capturedImage.value ?: return false
        val context = getApplication<Application>()

        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Vision_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LocalMeta")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create media store entry")

            resolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)) {
                    throw IOException("Failed to save bitmap")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            true
        } catch (e: Exception) {
            _errorMessage.value = "Failed to save image: ${e.message}"
            false
        }
    }

    fun clearError() {
        _errorMessage.value = null
        if (_viewState.value is ViewState.Error) {
            _viewState.value = if (_capturedImage.value != null) {
                ViewState.Capturing
            } else {
                ViewState.Idle
            }
        }
    }

    fun reset() {
        _capturedImage.value = null
        _analysisResult.value = null
        _viewState.value = ViewState.Idle
        _errorMessage.value = null
        _customPrompt.value = ""
    }

    fun refreshService() {
        visionService = null
        initializeService()
    }

    fun getDefaultPrompts(): List<String> = DEFAULT_PROMPTS

    override fun onCleared() {
        super.onCleared()
        _capturedImage.value?.recycle()
    }
}

/**
 * Helper extension: when the vision call succeeded via the fallback, the UI
 * wants to know which provider actually answered. The [VisionResult.Success]
 * payload doesn't carry it directly, so we ask the orchestrator (the
 * ViewModel) to record it. We default to the empty string if the fallback
 * itself was null (shouldn't happen in practice — guarded by the caller).
 */
private fun VisionResult.Success.attemptedNameOrEmpty(fallback: com.smartview.glassai.managers.ProviderConfig?): String =
    fallback?.displayName.orEmpty()
