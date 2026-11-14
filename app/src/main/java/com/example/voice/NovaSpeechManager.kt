package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NovaSpeechManager"

sealed interface SpeechMode {
    data object Stopped : SpeechMode
    data object WakeWordStandby : SpeechMode
    data object ActiveCommand : SpeechMode
}

class NovaSpeechManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private val _mode = MutableStateFlow<SpeechMode>(SpeechMode.Stopped)
    val mode: StateFlow<SpeechMode> = _mode.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var onCommandCaptured: ((String) -> Unit)? = null
    private var onWakeWordDetected: ((String) -> Unit)? = null
    private var onPartialText: ((String) -> Unit)? = null
    private var onSpeechError: ((String) -> Unit)? = null
    private var currentLanguageTag: String = "id-ID"

    private var isRestartScheduled = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startCommandListening(
        languageTag: String = "id-ID",
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        mainHandler.post {
            if (!hasRecordPermission()) {
                onError("Izin mikrofon belum diberikan.")
                return@post
            }
            if (!isAvailable()) {
                onError("Layanan pengenal suara tidak tersedia di perangkat ini.")
                return@post
            }
            cancelScheduledRestart()
            currentLanguageTag = languageTag
            onPartialText = onPartial
            onCommandCaptured = onResult
            onWakeWordDetected = null
            onSpeechError = onError
            _mode.value = SpeechMode.ActiveCommand
            startInternal(preferOffline = false)
        }
    }

    fun startWakeWordStandby(
        languageTag: String = "id-ID",
        onWakeWord: (commandAfterWake: String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        mainHandler.post {
            if (!hasRecordPermission() || !isAvailable()) {
                _mode.value = SpeechMode.Stopped
                return@post
            }
            if (_mode.value == SpeechMode.WakeWordStandby) {
                onWakeWordDetected = onWakeWord
                return@post
            }
            cancelScheduledRestart()
            currentLanguageTag = languageTag
            onWakeWordDetected = onWakeWord
            onCommandCaptured = null
            onPartialText = null
            onSpeechError = onError
            _mode.value = SpeechMode.WakeWordStandby
            startInternal(preferOffline = false)
        }
    }

    fun stopListening() {
        mainHandler.post {
            cancelScheduledRestart()
            _mode.value = SpeechMode.Stopped
            _audioLevel.value = 0f
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer", e)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            cancelScheduledRestart()
            _mode.value = SpeechMode.Stopped
            _audioLevel.value = 0f
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying recognizer", e)
            }
            recognizer = null
        }
    }

    private fun startInternal(preferOffline: Boolean) {
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(createListener())
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            val prevMode = _mode.value
            _mode.value = SpeechMode.Stopped
            if (prevMode == SpeechMode.ActiveCommand) {
                onSpeechError?.invoke("Gagal memulai mikrofon: ${e.localizedMessage ?: "error"}")
            }
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _audioLevel.value = 0.1f
        }

        override fun onBeginningOfSpeech() {
            _audioLevel.value = 0.35f
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            _audioLevel.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _audioLevel.value = 0.1f
        }

        override fun onError(error: Int) {
            _audioLevel.value = 0f
            val currentMode = _mode.value
            if (currentMode == SpeechMode.WakeWordStandby) {
                val delayMs = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 350L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> 1200L
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        _mode.value = SpeechMode.Stopped
                        onSpeechError?.invoke("Izin mikrofon dibutuhkan untuk Hi Nova.")
                        return
                    }
                    else -> 900L
                }
                scheduleStandbyRestart(delayMs)
            } else if (currentMode == SpeechMode.ActiveCommand) {
                _mode.value = SpeechMode.Stopped
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Suara tidak terdengar. Coba ucapkan lagi."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Koneksi internet dibutuhkan untuk mengenali suara."
                    SpeechRecognizer.ERROR_AUDIO -> "Gagal membaca audio mikrofon."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diaktifkan."
                    else -> "Pengenalan suara terhenti (kode $error)."
                }
                onSpeechError?.invoke(message)
            }
        }

        override fun onResults(results: Bundle?) {
            _audioLevel.value = 0f
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            val best = matches.firstOrNull()?.trim().orEmpty()

            when (_mode.value) {
                SpeechMode.ActiveCommand -> {
                    _mode.value = SpeechMode.Stopped
                    if (best.isNotEmpty()) {
                        onCommandCaptured?.invoke(best)
                    } else {
                        onSpeechError?.invoke("Suara tidak tertangkap. Silakan coba lagi.")
                    }
                }

                SpeechMode.WakeWordStandby -> {
                    val matchedWake = matches
                        .asSequence()
                        .map { WakeWordMatcher.match(it) }
                        .firstOrNull { it.matched }

                    if (matchedWake != null) {
                        _mode.value = SpeechMode.Stopped
                        onWakeWordDetected?.invoke(matchedWake.commandAfterWakeWord)
                    } else {
                        scheduleStandbyRestart(300L)
                    }
                }

                SpeechMode.Stopped -> Unit
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isEmpty()) return

            when (_mode.value) {
                SpeechMode.ActiveCommand -> onPartialText?.invoke(partial)
                SpeechMode.WakeWordStandby -> {
                    val wake = WakeWordMatcher.match(partial)
                    if (wake.matched && wake.commandAfterWakeWord.isEmpty()) {
                        // Wait briefly for final utterance so full command after "Hi Nova" can be captured
                        Log.d(TAG, "Partial wake word detected: $partial")
                    }
                }
                SpeechMode.Stopped -> Unit
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun scheduleStandbyRestart(delayMs: Long) {
        if (isRestartScheduled || _mode.value != SpeechMode.WakeWordStandby) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (_mode.value == SpeechMode.WakeWordStandby) {
                startInternal(preferOffline = false)
            }
        }, delayMs)
    }

    private fun cancelScheduledRestart() {
        isRestartScheduled = false
        mainHandler.removeCallbacksAndMessages(null)
    }
}
