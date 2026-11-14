package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "NovaTtsManager"

class NovaTtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null
    private var pendingOnComplete: (() -> Unit)? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                configureListener()
                pendingText?.let { text ->
                    val callback = pendingOnComplete
                    pendingText = null
                    pendingOnComplete = null
                    speak(text, callback)
                }
            } else {
                Log.e(TAG, "TextToSpeech init failed with status=$status")
            }
        }
    }

    private fun configureListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                pendingOnComplete?.invoke()
                pendingOnComplete = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                pendingOnComplete?.invoke()
                pendingOnComplete = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                pendingOnComplete?.invoke()
                pendingOnComplete = null
            }
        })
    }

    fun speak(rawText: String, onComplete: (() -> Unit)? = null) {
        val cleanText = sanitizeForSpeech(rawText)
        if (cleanText.isBlank()) {
            onComplete?.invoke()
            return
        }
        if (!isInitialized) {
            pendingText = cleanText
            pendingOnComplete = onComplete
            return
        }
        val engine = tts ?: run {
            onComplete?.invoke()
            return
        }

        val targetLocale = detectSpeechLocale(cleanText)
        val availability = engine.isLanguageAvailable(targetLocale)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = targetLocale
        } else {
            val fallback = Locale.forLanguageTag("id-ID")
            if (engine.isLanguageAvailable(fallback) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = fallback
            }
        }

        pendingOnComplete = onComplete
        _isSpeaking.value = true
        val params = Bundle()
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }

    fun stop() {
        pendingText = null
        pendingOnComplete = null
        _isSpeaking.value = false
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        tts = null
        isInitialized = false
    }

    private fun detectSpeechLocale(text: String): Locale {
        val lower = text.lowercase(Locale.ROOT)
        val englishSignals = listOf(" the ", " is ", " are ", " you ", " can ", " with ", " that ", " this ", " hello")
        val indonesianSignals = listOf(" yang ", " dan ", " untuk ", " dengan ", " saya ", " kamu ", " ini ", " itu ", " halo", " bisa ")
        val enScore = englishSignals.count { lower.contains(it) }
        val idScore = indonesianSignals.count { lower.contains(it) }
        return if (enScore > idScore) Locale.US else Locale.forLanguageTag("id-ID")
    }

    private fun sanitizeForSpeech(input: String): String {
        return input
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
