package com.smartview.glassai.viewmodels

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartview.glassai.managers.APIProviderManager
import com.smartview.glassai.managers.ProviderConfig
import com.smartview.glassai.utils.APIKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Drives the voice assistant ("Jarvis") feature.
 *
 * Flow: tap mic → SpeechRecognizer → text transcription → Ollama/Gemma 4
 *       → parse response → show answer / execute action.
 *
 * Actions are dispatched as Android [Intent]s or [MacroDroid](https://macrodroid.app)
 * broadcast intents when the AI response contains an ACTION| prefix.
 */
class AssistantViewModel(
    private val providerManager: APIProviderManager,
    private val apiKeyManager: APIKeyManager
) : ViewModel() {

    companion object {
        private const val TAG = "AssistantVM"
        private const val SYSTEM_PROMPT = """You are Jarvis, a voice assistant running locally on the user's phone.
You can answer questions, set timers, open apps, and trigger MacroDroid macros.

When the user asks you to DO something, respond with EXACTLY one of these formats:

For opening apps:
ACTION|OPEN_APP|com.spotify.music
ACTION|OPEN_APP|com.google.android.apps.maps
ACTION|OPEN_APP|com.whatsapp

For timers:
ACTION|TIMER|5|minutes
ACTION|TIMER|30|seconds

For alarms:
ACTION|ALARM|07:30|morning

For calling:
ACTION|CALL|contact name

For searching:
ACTION|SEARCH|bakery near me

For MacroDroid macros (custom shortcuts):
ACTION|MACRO|macro_name

For simple answers that don't need an action:
Just answer naturally in a friendly way. Keep responses under 3 sentences.

Use the user's language. If they speak French, respond in French. If English, respond in English."""
    }

    // MARK: - State

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _response = MutableStateFlow<String?>(null)
    val response: StateFlow<String?> = _response.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: MutableSharedFlow<String> = _error

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentAction: PendingAction? = null

    data class PendingAction(
        val type: String,
        val value: String,
        val label: String
    )

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    fun clearPendingAction() {
        _pendingAction.value = null
    }

    // MARK: - Start listening

    fun startListening(context: Context) {
        if (_isListening.value) return
        _response.value = null
        _transcript.value = ""
        _error.tryEmit("") // clear errors

        if (SpeechRecognizer.isRecognitionAvailable(context).not()) {
            _error.tryEmit("Speech recognition is not available on this device")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")   // will detect anyway
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR,en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
            }

            override fun onError(errorCode: Int) {
                _isListening.value = false
                val msg = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    else -> "Recognition error: $errorCode"
                }
                _error.tryEmit(msg)
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!texts.isNullOrEmpty()) {
                    _transcript.value = texts[0]
                    sendToAi(texts[0])
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!texts.isNullOrEmpty()) {
                    _transcript.value = texts[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListening.value = false
    }

    // MARK: - Send to AI

    private fun sendToAi(text: String) {
        viewModelScope.launch {
            _isThinking.value = true
            try {
                val config = providerManager.currentFallbackConfig(apiKeyManager)
                    ?: providerManager.currentPrimaryConfig(apiKeyManager)
                if (config == null) {
                    _error.tryEmit("No AI provider configured. Set up a local server in Settings.")
                    _isThinking.value = false
                    return@launch
                }

                val result = queryOllama(config, text)
                when (result) {
                    is AiResult.Answer -> {
                        _response.value = result.text
                    }
                    is AiResult.Action -> {
                        _response.value = result.displayText
                        _pendingAction.value = PendingAction(
                            type = result.actionType,
                            value = result.actionValue,
                            label = result.displayText
                        )
                    }
                    is AiResult.Error -> {
                        _error.tryEmit(result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI query failed", e)
                _error.tryEmit("AI error: ${e.message ?: "Unknown error"}")
            } finally {
                _isThinking.value = false
            }
        }
    }

    // MARK: - Execute action

    fun executeAction(context: Context, action: PendingAction) {
        try {
            when (action.type) {
                "OPEN_APP" -> {
                    val intent = context.packageManager.getLaunchIntentForPackage(action.value)
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        // Try as a search / market URL
                        val search = Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse("market://details?id=${action.value}")
                        }
                        context.startActivity(search)
                    }
                }
                "TIMER" -> {
                    val parts = action.value.split("|")
                    val duration = parts.getOrNull(0)?.toIntOrNull() ?: 5
                    val unit = parts.getOrNull(1) ?: "minutes"
                    val seconds = if (unit == "minutes") duration * 60 else duration
                    // Use Android CountDownTimer via notification
                    startTimerNotification(context, seconds, action.label)
                }
                "SEARCH" -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://www.google.com/search?q=${java.net.URLEncoder.encode(action.value, "UTF-8")}")
                    }
                    context.startActivity(intent)
                }
                "MACRO" -> {
                    // MacroDroid broadcast intent
                    val intent = Intent("com.android.macrodroid.action.TRIGGER_MACRO").apply {
                        putExtra("com.android.macrodroid.MACRO_NAME", action.value)
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    }
                    context.sendBroadcast(intent)
                    // Also try alternative package
                    val alt = Intent("com.android.macrodroid.action.TRIGGER_MACRO").apply {
                        `package` = "com.arlosoft.macrodroid"
                        putExtra("com.android.macrodroid.MACRO_NAME", action.value)
                    }
                    context.sendBroadcast(alt)
                }
                else -> {
                    _error.tryEmit("Unknown action: ${action.type}")
                }
            }
            _pendingAction.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Action execution failed", e)
            _error.tryEmit("Action failed: ${e.message}")
        }
    }

    private fun startTimerNotification(context: Context, seconds: Int, label: String) {
        // Simplified: post a system notification that shows a countdown
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "jarvis_timers"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                android.app.NotificationChannel(channelId, "Timers", android.app.NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = android.app.Notification.Builder(context, channelId)
            .setContentTitle("⏱ Timer: $seconds seconds")
            .setContentText(label)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
        notificationManager.notify(1001, notification)
    }

    // MARK: - Ollama query

    private suspend fun queryOllama(config: ProviderConfig, userText: String): AiResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(config.baseURL.trimEnd('/') + "/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            val body = JSONObject().apply {
                put("model", config.model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userText)
                    })
                })
                put("temperature", 0.3)
                put("max_tokens", 512)
                put("stream", false)
            }

            conn.outputStream.write(body.toString().toByteArray())

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                return@withContext AiResult.Error("HTTP $responseCode: $errorBody")
            }

            val responseText = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(responseText)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            // Parse action prefix
            if (content.startsWith("ACTION|")) {
                val parts = content.split("|", limit = 3)
                if (parts.size >= 3) {
                    val actionType = parts[1]
                    val actionValue = parts[2]
                    val displayText = when (actionType) {
                        "OPEN_APP" -> "▶ Opening $actionValue"
                        "TIMER" -> "⏱ Timer set"
                        "ALARM" -> "⏰ Alarm set"
                        "SEARCH" -> "🔍 Searching for ${actionValue}"
                        "MACRO" -> "⚡ Running macro: ${actionValue}"
                        "CALL" -> "📞 Calling ${actionValue}"
                        else -> "🎯 Action: ${actionType}"
                    }
                    return@withContext AiResult.Action(actionType, actionValue, displayText)
                }
            }

            return@withContext AiResult.Answer(content)
        } catch (e: java.net.ConnectException) {
            AiResult.Error("Cannot reach the AI server. Check your local server is running and the URL is correct.")
        } catch (e: java.net.SocketTimeoutException) {
            AiResult.Error("AI server timed out. The model may be too large or the server is overloaded.")
        } catch (e: Exception) {
            AiResult.Error("AI query failed: ${e.message ?: "Unknown error"}")
        }
    }

    private sealed class AiResult {
        data class Answer(val text: String) : AiResult()
        data class Action(val actionType: String, val actionValue: String, val displayText: String) : AiResult()
        data class Error(val message: String) : AiResult()
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
