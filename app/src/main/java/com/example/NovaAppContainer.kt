package com.example

import android.content.Context
import com.example.ai.NovaAiRepository
import com.example.data.ChatRepository
import com.example.data.NovaSettingsRepository
import com.example.data.local.NovaDatabase
import com.example.overlay.HomeVisibilityMonitor
import com.example.voice.NovaTtsManager

class NovaAppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NovaDatabase by lazy {
        NovaDatabase.getInstance(appContext)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(database.novaDao())
    }

    val settingsRepository: NovaSettingsRepository by lazy {
        NovaSettingsRepository(appContext)
    }

    val aiRepository: NovaAiRepository by lazy {
        NovaAiRepository()
    }

    val homeVisibilityMonitor: HomeVisibilityMonitor by lazy {
        HomeVisibilityMonitor(appContext)
    }

    val ttsManager: NovaTtsManager by lazy {
        NovaTtsManager(appContext)
    }
}
