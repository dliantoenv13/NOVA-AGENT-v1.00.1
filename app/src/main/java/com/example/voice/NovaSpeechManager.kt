package com.example.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val TAG = "NovaSpeechManager"

sealed interface SpeechMode {
    data object Stopped : SpeechMode
    data object WakeWordVadStandby : SpeechMode
    data object WakeWordSpeechVerify : SpeechMode
    data object ActiveCommand : SpeechMode
}

class NovaSpeechManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val vadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recognizer: SpeechRecognizer? = null
    private var vadJob: Job? = null

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
        stopVadOnly()
        mainHandler.post {
            if (!hasRecordPermission()) {
                onError("Izin mikrofon belum diberikan.")
                return@post
            }
            if (!isAvailable()) {
                onError("Layanan pengenal suara (Google Speech) tidak tersedia di perangkat ini.")
                return@post
            }
            cancelMainCallbacks()
            currentLanguageTag = languageTag
            onPartialText = onPartial
            onCommandCaptured = onResult
            onWakeWordDetected = null
            onSpeechError = onError
            _mode.value = SpeechMode.ActiveCommand
            startSpeechRecognizerInternal()
        }
    }

    fun startWakeWordStandby(
        languageTag: String = "id-ID",
        onWakeWord: (commandAfterWake: String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (!hasRecordPermission() || !isAvailable()) {
            _mode.value = SpeechMode.Stopped
            return
        }
        currentLanguageTag = languageTag
        onWakeWordDetected = onWakeWord
        onCommandCaptured = null
        onPartialText = null
        onSpeechError = onError

        if (_mode.value == SpeechMode.WakeWordVadStandby ||
            _mode.value == SpeechMode.WakeWordSpeechVerify
        ) {
            return
        }

        mainHandler.post {
            cancelMainCallbacks()
            destroyRecognizerOnMain()
            _mode.value = SpeechMode.WakeWordVadStandby
            startVadLoop()
        }
    }

    fun stopListening() {
        stopVadOnly()
        mainHandler.post {
            cancelMainCallbacks()
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
        stopVadOnly()
        mainHandler.post {
            cancelMainCallbacks()
            _mode.value = SpeechMode.Stopped
            _audioLevel.value = 0f
            destroyRecognizerOnMain()
        }
    }

    private fun stopVadOnly() {
        vadJob?.cancel()
        vadJob = null
    }

    @SuppressLint("MissingPermission")
    private fun startVadLoop() {
        stopVadOnly()
        vadJob = vadScope.launch {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                // Fallback to direct SpeechRecognizer if AudioRecord unavailable
                mainHandler.post {
                    if (_mode.value == SpeechMode.WakeWordVadStandby) {
                        _mode.value = SpeechMode.WakeWordSpeechVerify
                        startSpeechRecognizerInternal()
                    }
                }
                return@launch
            }

            val bufferSize = (minBuffer * 2).coerceAtLeast(2048)
            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release()
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release()
                    mainHandler.post {
                        if (_mode.value == SpeechMode.WakeWordVadStandby) {
                            _mode.value = SpeechMode.WakeWordSpeechVerify
                            startSpeechRecognizerInternal()
                        }
                    }
                    return@launch
                }

                val samples = ShortArray(1024)
                audioRecord.startRecording()
                var hotFrames = 0

                while (isActive && _mode.value == SpeechMode.WakeWordVadStandby) {
                    val read = audioRecord.read(samples, 0, samples.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = samples[i].toDouble()
                            sumSq += s * s
                        }
                        val rms = sqrt(sumSq / read)
                        _audioLevel.value = ((rms - 250.0) / 3000.0).toFloat().coerceIn(0f, 1f)

                        if (rms > 850.0) {
                            hotFrames++
                            if (hotFrames >= 2) {
                                // Speech energy detected -> release AudioRecord & verify "Hi Nova" via SpeechRecognizer
                                break
                            }
                        } else {
                            hotFrames = (hotFrames - 1).coerceAtLeast(0)
                        }
                    } else {
                        delay(40)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord VAD error, falling back", e)
            } finally {
                try {
                    audioRecord?.stop()
                } catch (_: Exception) {
                }
                try {
                    audioRecord?.release()
                } catch (_: Exception) {
                }
            }

            if (isActive && _mode.value == SpeechMode.WakeWordVadStandby) {
                mainHandler.post {
                    if (_mode.value == SpeechMode.WakeWordVadStandby) {
                        _mode.value = SpeechMode.WakeWordSpeechVerify
                        startSpeechRecognizerInternal()
                    }
                }
            }
        }
    }

    private fun startSpeechRecognizerInternal() {
        try {
            destroyRecognizerOnMain()
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
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            val prevMode = _mode.value
            _mode.value = SpeechMode.Stopped
            if (prevMode == SpeechMode.ActiveCommand) {
                onSpeechError?.invoke("Gagal memulai mikrofon: ${e.localizedMessage ?: "error"}")
            } else if (prevMode == SpeechMode.WakeWordSpeechVerify) {
                scheduleReturnToVad(1200L)
            }
        }
    }

    private fun destroyRecognizerOnMain() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _audioLevel.value = 0.12f
        }

        override fun onBeginningOfSpeech() {
            _audioLevel.value = 0.35f
        }

        override fun onRmsChanged(rmsdB: Float) {
            _audioLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _audioLevel.value = 0.1f
        }

        override fun onError(error: Int) {
            _audioLevel.value = 0f
            when (_mode.value) {
                SpeechMode.WakeWordSpeechVerify -> {
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        _mode.value = SpeechMode.Stopped
                        onSpeechError?.invoke("Izin mikrofon dibutuhkan untuk Hi Nova.")
                    } else {
                        val cooldown = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                            error == SpeechRecognizer.ERROR_CLIENT
                        ) 1200L else 450L
                        scheduleReturnToVad(cooldown)
                    }
                }

                SpeechMode.ActiveCommand -> {
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

                else -> Unit
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

                SpeechMode.WakeWordSpeechVerify -> {
                    val matchedWake = matches
                        .asSequence()
                        .map { WakeWordMatcher.match(it) }
                        .firstOrNull { it.matched }

                    if (matchedWake != null) {
                        _mode.value = SpeechMode.Stopped
                        onWakeWordDetected?.invoke(matchedWake.commandAfterWakeWord)
                    } else {
                        scheduleReturnToVad(350L)
                    }
                }

                else -> Unit
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isEmpty()) return

            if (_mode.value == SpeechMode.ActiveCommand) {
                onPartialText?.invoke(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun scheduleReturnToVad(delayMs: Long) {
        if (isRestartScheduled) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (_mode.value == SpeechMode.WakeWordSpeechVerify && hasRecordPermission()) {
                destroyRecognizerOnMain()
                _mode.value = SpeechMode.WakeWordVadStandby
                startVadLoop()
            }
        }, delayMs)
    }

    private fun cancelMainCallbacks() {
        isRestartScheduled = false
        mainHandler.removeCallbacksAndMessages(null)
    }
}
