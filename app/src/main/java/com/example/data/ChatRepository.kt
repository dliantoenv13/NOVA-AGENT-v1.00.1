package com.example.data

import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.NovaDao
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: NovaDao) {

    val sessions: Flow<List<ChatSessionEntity>> = dao.observeSessions()

    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>> =
        dao.observeMessagesForSession(sessionId)

    suspend fun getRecentMessages(sessionId: Long, limit: Int = 20): List<ChatMessageEntity> =
        dao.getMessagesForSession(sessionId, limit)

    suspend fun getOrCreateDefaultSession(preferredSessionId: Long?): Long {
        if (preferredSessionId != null) {
            val existing = dao.getSessionById(preferredSessionId)
            if (existing != null) return existing.id
        }
        val latest = dao.getLatestSession()
        if (latest != null) return latest.id
        return createNewSession()
    }

    suspend fun createNewSession(title: String = "Obrolan Baru"): Long {
        val now = System.currentTimeMillis()
        return dao.insertSession(
            ChatSessionEntity(
                title = title,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun addUserMessage(
        sessionId: Long,
        text: String,
        isVoiceMessage: Boolean
    ): Long {
        val existingMessages = dao.getMessagesForSession(sessionId, limit = 1)
        val now = System.currentTimeMillis()
        if (existingMessages.isEmpty()) {
            val derivedTitle = text.trim()
                .replace("\n", " ")
                .take(36)
                .ifBlank { "Obrolan Suara" }
            dao.updateSessionMetadata(sessionId, derivedTitle, now)
        } else {
            dao.touchSession(sessionId, now)
        }
        return dao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ROLE_USER,
                content = text,
                isVoiceMessage = isVoiceMessage,
                timestamp = now
            )
        )
    }

    suspend fun insertNovaPlaceholder(sessionId: Long, isVoiceMessage: Boolean): Long {
        val now = System.currentTimeMillis()
        dao.touchSession(sessionId, now)
        return dao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ROLE_NOVA,
                content = "",
                isVoiceMessage = isVoiceMessage,
                timestamp = now
            )
        )
    }

    suspend fun updateNovaMessage(messageId: Long, content: String, isError: Boolean = false) {
        dao.updateMessageContent(messageId, content, isError)
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSessionById(sessionId)
    }

    companion object {
        const val ROLE_USER = "USER"
        const val ROLE_NOVA = "NOVA"
    }
}
