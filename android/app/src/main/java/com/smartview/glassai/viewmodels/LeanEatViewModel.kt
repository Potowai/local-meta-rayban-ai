package com.smartview.glassai.viewmodels

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.smartview.glassai.data.FoodHistoryStorage
import com.smartview.glassai.managers.APIProviderManager
import com.smartview.glassai.models.FoodEntry
import com.smartview.glassai.models.FoodNutritionResponse
import com.smartview.glassai.services.LeanEatResult
import com.smartview.glassai.services.LeanEatService
import com.smartview.glassai.utils.APIKeyManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class LeanEatViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyManager = APIKeyManager.getInstance(application)
    private val providerManager = APIProviderManager.getInstance(application)
    private var leanEatService: LeanEatService? = null
    private val foodHistoryStorage = FoodHistoryStorage.getInstance(application)
    private val gson = Gson()

    // State
    sealed class ViewState {
        object Idle : ViewState()
        object Capturing : ViewState()
        object Analyzing : ViewState()
        data class Result(val response: FoodNutritionResponse) : ViewState()
        data class Error(val message: String) : ViewState()
    }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Idle)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage: StateFlow<Bitmap?> = _capturedImage.asStateFlow()

    private val _nutritionResult = MutableStateFlow<FoodNutritionResponse?>(null)
    val nutritionResult: StateFlow<FoodNutritionResponse?> = _nutritionResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // Food history
    private val _foodHistory = MutableStateFlow<List<FoodEntry>>(emptyList())
    val foodHistory: StateFlow<List<FoodEntry>> = _foodHistory.asStateFlow()

    // Save indicator (one-shot)
    private val _saveConfirmation = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saveConfirmation: SharedFlow<String> = _saveConfirmation

    /** Emits a one-shot signal when the nutrition call fell back from primary to local server. */
    private val _fallbackNotice = MutableSharedFlow<VisionViewModel.FallbackNotice>(extraBufferCapacity = 1)
    val fallbackNotice: SharedFlow<VisionViewModel.FallbackNotice> = _fallbackNotice

    init {
        initializeService()
        loadFoodHistory()
    }

    private fun initializeService() {
        leanEatService = null
    }

    private fun loadFoodHistory() {
        val entries = foodHistoryStorage.getAllEntries()
        _foodHistory.value = entries
    }

    fun setCapturedImage(bitmap: Bitmap) {
        _capturedImage.value = bitmap
        _viewState.value = ViewState.Capturing
        _nutritionResult.value = null
        _errorMessage.value = null
    }

    fun analyzeFood() {
        val image = _capturedImage.value
        if (image == null) {
            _errorMessage.value = "No image captured"
            return
        }

        viewModelScope.launch {
            _viewState.value = ViewState.Analyzing
            _isAnalyzing.value = true

            try {
                val primary = providerManager.currentPrimaryConfig(apiKeyManager)
                val fallback = if (providerManager.fallbackEnabled.value) {
                    providerManager.currentFallbackConfig(apiKeyManager)
                } else null

                val service = leanEatService ?: LeanEatService(
                    apiKey = primary.apiKey,
                    providerManager = providerManager
                )

                val result = service.analyzeFoodWithFallback(
                    image = image,
                    primary = primary,
                    fallback = fallback
                )

                when (result) {
                    is LeanEatResult.Success -> {
                        if (result.usedFallback) {
                            _fallbackNotice.tryEmit(
                                VisionViewModel.FallbackNotice(
                                    primaryName = primary.displayName,
                                    fallbackName = fallback?.displayName.orEmpty()
                                )
                            )
                        }
                        _nutritionResult.value = result.response
                        _viewState.value = ViewState.Result(result.response)
                        // Auto-save to history
                        saveCurrentResultToHistory()
                    }
                    is LeanEatResult.Failure -> {
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

    /**
     * Save the current analysis result to food history.
     */
    private fun saveCurrentResultToHistory() {
        val response = _nutritionResult.value ?: return
        val bitmap = _capturedImage.value
        val responseJson = try {
            gson.toJson(response)
        } catch (_: Exception) {
            ""
        }

        val success = foodHistoryStorage.saveEntry(
            bitmap = bitmap,
            calories = response.totalCalories,
            protein = response.totalProtein,
            carbs = response.totalCarbs,
            fat = response.totalFat,
            healthScore = response.healthScore,
            foods = response.foods,
            suggestions = response.suggestions,
            responseJson = responseJson
        )

        if (success) {
            loadFoodHistory()
            _saveConfirmation.tryEmit("Saved to history ✓")
        }
    }

    fun deleteFoodEntry(id: String) {
        foodHistoryStorage.deleteEntry(id)
        loadFoodHistory()
    }

    fun clearAllHistory() {
        foodHistoryStorage.deleteAllEntries()
        loadFoodHistory()
    }

    fun retakePhoto() {
        _capturedImage.value = null
        _nutritionResult.value = null
        _viewState.value = ViewState.Idle
        _errorMessage.value = null
    }

    fun saveImageToGallery(): Boolean {
        val bitmap = _capturedImage.value ?: return false
        val context = getApplication<Application>()

        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "LeanEat_${System.currentTimeMillis()}.jpg")
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

            _saveConfirmation.tryEmit("Saved to gallery ✓")
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
        _nutritionResult.value = null
        _viewState.value = ViewState.Idle
        _errorMessage.value = null
    }

    fun refreshService() {
        leanEatService = null
        initializeService()
    }

    override fun onCleared() {
        super.onCleared()
        _capturedImage.value?.recycle()
    }
}
