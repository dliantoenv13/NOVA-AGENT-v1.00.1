package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.novaDataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

data class NovaSettings(
    val isCapsuleEnabled: Boolean = true,
    val isWakeWordEnabled: Boolean = true,
    val isTtsEnabled: Boolean = true,
    val isLowEndModeEnabled: Boolean = false,
    val voiceLanguageTag: String = "id-ID",
    val activeSessionId: Long? = null
)

class NovaSettingsRepository(private val context: Context) {

    private object Keys {
        val CAPSULE_ENABLED = booleanPreferencesKey("capsule_enabled")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val LOW_END_MODE = booleanPreferencesKey("low_end_mode")
        val VOICE_LANG = stringPreferencesKey("voice_language_tag")
        val ACTIVE_SESSION_ID = longPreferencesKey("active_session_id")
    }

    val settings: Flow<NovaSettings> = context.novaDataStore.data.map { prefs ->
        NovaSettings(
            isCapsuleEnabled = prefs[Keys.CAPSULE_ENABLED] ?: true,
            isWakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: true,
            isTtsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            isLowEndModeEnabled = prefs[Keys.LOW_END_MODE] ?: false,
            voiceLanguageTag = prefs[Keys.VOICE_LANG] ?: "id-ID",
            activeSessionId = prefs[Keys.ACTIVE_SESSION_ID]
        )
    }

    suspend fun setCapsuleEnabled(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.CAPSULE_ENABLED] = enabled }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setLowEndModeEnabled(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.LOW_END_MODE] = enabled }
    }

    suspend fun setVoiceLanguageTag(tag: String) {
        context.novaDataStore.edit { it[Keys.VOICE_LANG] = tag }
    }

    suspend fun setActiveSessionId(sessionId: Long) {
        context.novaDataStore.edit { it[Keys.ACTIVE_SESSION_ID] = sessionId }
    }
}
