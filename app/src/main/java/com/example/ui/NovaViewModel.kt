package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.NovaAppContainer
import com.example.ai.AiStreamChunk
import com.example.ai.isFirebaseConfigured
import com.example.data.NovaSettings
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.overlay.NovaFloatingCapsuleService
import com.example.overlay.NovaPermissionState
import com.example.voice.SpeechMode
import com.example.voice.WakeWordMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "NovaViewModel"

sealed interface AssistantStatus {
    data object Idle : AssistantStatus
    data class Listening(val partialTranscript: String = "") : AssistantStatus
    data object Thinking : AssistantStatus
    data object Speaking : AssistantStatus
    data class Error(val message: String) : AssistantStatus
}

@OptIn(ExperimentalCoroutinesApi::class)
class NovaViewModel(
    application: Application,
    private val container: NovaAppContainer
) : AndroidViewModel(application) {

    private val speechManager = container.speechManager

    val settings: StateFlow<NovaSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NovaSettings())

    val sessions: StateFlow<List<ChatSessionEntity>> = container.chatRepository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val permissionState: StateFlow<NovaPermissionState> =
        container.homeVisibilityMonitor.permissionState

    val isOnHomeScreen: StateFlow<Boolean> =
        container.homeVisibilityMonitor.isOnHomeScreen

    val isSpeaking: StateFlow<Boolean> = container.ttsManager.isSpeaking

    val audioLevel: StateFlow<Float> = speechManager.audioLevel

    val speechMode: StateFlow<SpeechMode> = speechManager.mode

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val messages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .filterNotNull()
        .flatMapLatest { sessionId -> container.chatRepository.observeMessages(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _assistantStatus = MutableStateFlow<AssistantStatus>(AssistantStatus.Idle)
    val assistantStatus: StateFlow<AssistantStatus> = _assistantStatus.asStateFlow()

    private val _showPermissionsSheet = MutableStateFlow(false)
    val showPermissionsSheet: StateFlow<Boolean> = _showPermissionsSheet.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            settings.map { it.activeSessionId }
                .distinctUntilChanged()
                .collect { preferredId ->
                    val resolved = container.chatRepository.getOrCreateDefaultSession(preferredId)
                    if (_activeSessionId.value != resolved) {
                        _activeSessionId.value = resolved
                    }
                    if (preferredId != resolved) {
                        container.settingsRepository.setActiveSessionId(resolved)
                    }
                }
        }

        viewModelScope.launch {
            combine(isSpeaking, _assistantStatus) { speaking, status ->
                Pair(speaking, status)
            }.collect { (speaking, status) ->
                if (speaking && status is AssistantStatus.Idle) {
                    _assistantStatus.value = AssistantStatus.Speaking
                } else if (!speaking && status is AssistantStatus.Speaking) {
                    _assistantStatus.value = AssistantStatus.Idle
                }
            }
        }
    }

    fun updateComposerText(text: String) {
        _composerText.value = text
        if (_assistantStatus.value is AssistantStatus.Error) {
            _assistantStatus.value = AssistantStatus.Idle
        }
    }

    fun openPermissionsSheet() {
        container.homeVisibilityMonitor.refreshPermissions()
        _showPermissionsSheet.value = true
    }

    fun dismissPermissionsSheet() {
        _showPermissionsSheet.value = false
    }

    fun refreshPermissionsAndSyncService() {
        container.homeVisibilityMonitor.refreshPermissions()
        val currentSettings = settings.value
        val perms = permissionState.value
        if (currentSettings.isCapsuleEnabled && perms.hasRecordAudio && perms.hasOverlayPermission) {
            NovaFloatingCapsuleService.startIfPermitted(getApplication())
        } else if (!currentSettings.isCapsuleEnabled) {
            NovaFloatingCapsuleService.stop(getApplication())
        }
    }

    fun sendTextMessage() {
        val text = _composerText.value.trim()
        if (text.isEmpty()) return
        _composerText.value = ""
        submitPrompt(prompt = text, isVoiceMessage = false)
    }

    fun sendQuickPrompt(prompt: String) {
        submitPrompt(prompt = prompt, isVoiceMessage = false)
    }

    fun toggleVoiceRecording() {
        if (speechMode.value == SpeechMode.ActiveCommand) {
            speechManager.stopListening()
            _assistantStatus.value = AssistantStatus.Idle
            return
        }

        container.ttsManager.stop()
        _assistantStatus.value = AssistantStatus.Listening("")
        speechManager.startCommandListening(
            languageTag = settings.value.voiceLanguageTag,
            onPartial = { partial ->
                _assistantStatus.value = AssistantStatus.Listening(partial)
            },
            onResult = { transcript ->
                val wake = WakeWordMatcher.match(transcript)
                val finalPrompt = if (wake.matched && wake.commandAfterWakeWord.isNotBlank()) {
                    wake.commandAfterWakeWord
                } else {
                    transcript
                }
                submitPrompt(prompt = finalPrompt, isVoiceMessage = true)
            },
            onError = { errorMsg ->
                _assistantStatus.value = AssistantStatus.Error(errorMsg)
            }
        )
    }

    fun clearError() {
        if (_assistantStatus.value is AssistantStatus.Error) {
            _assistantStatus.value = AssistantStatus.Idle
        }
    }

    fun speakMessage(text: String) {
        _assistantStatus.value = AssistantStatus.Speaking
        container.ttsManager.speak(text) {
            if (_assistantStatus.value is AssistantStatus.Speaking) {
                _assistantStatus.value = AssistantStatus.Idle
            }
        }
    }

    fun stopSpeaking() {
        container.ttsManager.stop()
        if (_assistantStatus.value is AssistantStatus.Speaking) {
            _assistantStatus.value = AssistantStatus.Idle
        }
    }

    fun startNewChat() {
        activeGenerationJob?.cancel()
        container.ttsManager.stop()
        viewModelScope.launch {
            val newId = container.chatRepository.createNewSession()
            _activeSessionId.value = newId
            container.settingsRepository.setActiveSessionId(newId)
            _assistantStatus.value = AssistantStatus.Idle
        }
    }

    fun selectSession(sessionId: Long) {
        activeGenerationJob?.cancel()
        container.ttsManager.stop()
        viewModelScope.launch {
            _activeSessionId.value = sessionId
            container.settingsRepository.setActiveSessionId(sessionId)
            _assistantStatus.value = AssistantStatus.Idle
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            container.chatRepository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                val fallback = container.chatRepository.getOrCreateDefaultSession(null)
                _activeSessionId.value = fallback
                container.settingsRepository.setActiveSessionId(fallback)
            }
        }
    }

    fun setCapsuleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setCapsuleEnabled(enabled)
            if (enabled) {
                container.homeVisibilityMonitor.refreshPermissions()
                val perms = permissionState.value
                if (perms.allCapsulePermissionsReady) {
                    NovaFloatingCapsuleService.startIfPermitted(getApplication())
                } else {
                    _showPermissionsSheet.value = true
                }
            } else {
                NovaFloatingCapsuleService.stop(getApplication())
            }
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setWakeWordEnabled(enabled)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setTtsEnabled(enabled)
            if (!enabled) {
                container.ttsManager.stop()
            }
        }
    }

    fun setLowEndModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setLowEndModeEnabled(enabled)
        }
    }

    fun setVoiceLanguageTag(tag: String) {
        viewModelScope.launch {
            container.settingsRepository.setVoiceLanguageTag(tag)
        }
    }

    private fun submitPrompt(prompt: String, isVoiceMessage: Boolean) {
        activeGenerationJob?.cancel()
        container.ttsManager.stop()

        activeGenerationJob = viewModelScope.launch {
            if (!isFirebaseConfigured(getApplication())) {
                val msg = "Konfigurasi Firebase (app/google-services.json) belum terbawa pada APK hasil build ini. Tambahkan app/google-services.json atau GitHub Secret GOOGLE_SERVICES_JSON lalu build ulang."
                _assistantStatus.value = AssistantStatus.Error(msg)
                return@launch
            }

            _assistantStatus.value = AssistantStatus.Thinking
            val sessionId = _activeSessionId.value
                ?: container.chatRepository.getOrCreateDefaultSession(settings.value.activeSessionId)
            _activeSessionId.value = sessionId
            container.settingsRepository.setActiveSessionId(sessionId)

            val history = container.chatRepository.getRecentMessages(sessionId)
            container.chatRepository.addUserMessage(
                sessionId = sessionId,
                text = prompt,
                isVoiceMessage = isVoiceMessage
            )
            val novaMessageId = container.chatRepository.insertNovaPlaceholder(
                sessionId = sessionId,
                isVoiceMessage = isVoiceMessage
            )

            try {
                var finalText = ""
                container.aiRepository.streamChatReply(history, prompt).collect { chunk ->
                    when (chunk) {
                        is AiStreamChunk.Partial -> {
                            container.chatRepository.updateNovaMessage(
                                messageId = novaMessageId,
                                content = chunk.accumulatedText,
                                isError = false
                            )
                        }
                        is AiStreamChunk.Completed -> {
                            finalText = chunk.finalText
                            container.chatRepository.updateNovaMessage(
                                messageId = novaMessageId,
                                content = finalText,
                                isError = false
                            )
                        }
                    }
                }

                if (isVoiceMessage && settings.value.isTtsEnabled && finalText.isNotBlank()) {
                    _assistantStatus.value = AssistantStatus.Speaking
                    container.ttsManager.speak(finalText) {
                        if (_assistantStatus.value is AssistantStatus.Speaking) {
                            _assistantStatus.value = AssistantStatus.Idle
                        }
                    }
                } else {
                    _assistantStatus.value = AssistantStatus.Idle
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini streaming timed out", e)
                val msg = "Permintaan melebihi batas waktu 60 detik. Periksa koneksi internet Anda."
                container.chatRepository.updateNovaMessage(novaMessageId, msg, isError = true)
                _assistantStatus.value = AssistantStatus.Error(msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini streaming failed", e)
                val msg = e.localizedMessage ?: "Gagal terhubung ke Nova AI."
                container.chatRepository.updateNovaMessage(novaMessageId, msg, isError = true)
                _assistantStatus.value = AssistantStatus.Error(msg)
            }
        }
    }

    override fun onCleared() {
        if (speechManager.mode.value == SpeechMode.ActiveCommand) {
            speechManager.stopListening()
        }
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val container: NovaAppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NovaViewModel::class.java)) {
                return NovaViewModel(application, container) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
