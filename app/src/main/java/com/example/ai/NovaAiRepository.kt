package com.example.ai

import android.util.Log
import com.example.data.ChatRepository
import com.example.data.local.ChatMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "NovaAiRepository"

sealed interface AiStreamChunk {
    data class Partial(val accumulatedText: String) : AiStreamChunk
    data class Completed(val finalText: String) : AiStreamChunk
}

class NovaAiRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.7f
                maxOutputTokens = 1024
            },
            systemInstruction = content {
                text(
                    """
                    Kamu adalah Nova, asisten AI pribadi yang cerdas, modern, ringkas, dan responsif.
                    Aturan utama:
                    1. Gunakan Bahasa Indonesia yang natural dan modern secara default, namun jika pengguna bertanya atau berbicara dalam Bahasa Inggris, balaslah dalam Bahasa Inggris secara alami.
                    2. Jawab langsung ke inti permasalahan secara jelas dan ramah.
                    3. Untuk pertanyaan suara atau perintah cepat, hindari simbol markdown yang berlebihan seperti tabel atau banyak tanda bintang agar nyaman dibacakan dengan suara.
                    """.trimIndent()
                )
            }
        )
    }

    private val voiceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.6f
                maxOutputTokens = 320
            },
            systemInstruction = content {
                text(
                    """
                    Kamu adalah Nova yang sedang merespons melalui suara di latar belakang layar utama.
                    Berikan jawaban yang singkat, padat, alami untuk diucapkan (1 hingga 3 kalimat kecuali diminta detail), tanpa format markdown, tanpa bullet list, dan tanpa emoji.
                    Ikuti bahasa pengguna (Indonesia atau Inggris).
                    """.trimIndent()
                )
            }
        )
    }

    fun streamChatReply(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<AiStreamChunk> = flow {
        val contents = buildContents(history, userPrompt)
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chatModel.generateContentStream(contents).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(AiStreamChunk.Partial(accumulated.toString()))
                }
            }
        }
        val finalResult = accumulated.toString().trim()
        if (finalResult.isEmpty()) {
            throw IllegalStateException("Nova tidak menerima teks jawaban dari Gemini.")
        }
        emit(AiStreamChunk.Completed(finalResult))
    }

    suspend fun generateVoiceBackgroundReply(
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): String {
        return try {
            val contents = buildContents(history, userPrompt)
            val response = withTimeout(45_000L) {
                voiceModel.generateContent(contents)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                throw IllegalStateException("Respons suara kosong dari Nova.")
            }
            text
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice AI request timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice AI generation failed", e)
            throw e
        }
    }

    private fun buildContents(
        history: List<ChatMessageEntity>,
        latestPrompt: String
    ): List<Content> {
        val list = mutableListOf<Content>()
        history.filter { !it.isError && it.content.isNotBlank() }
            .takeLast(12)
            .forEach { msg ->
                val role = if (msg.role == ChatRepository.ROLE_USER) "user" else "model"
                list.add(content(role) { text(msg.content) })
            }
        list.add(content("user") { text(latestPrompt) })
        return list
    }
}
