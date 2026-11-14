package com.example.voice

data class WakeMatchResult(
    val matched: Boolean,
    val commandAfterWakeWord: String = ""
)

object WakeWordMatcher {

    // Matches "Hi Nova" and common Indonesian STT phonetic spellings ("hai nova", "hay nova", "hye nova", "hai nofa")
    private val WAKE_REGEX = Regex(
        pattern = """\b(?:hi|hai|hay|hye)[,\s]+no[fv]a\b[,.:!?\s]*(.*)""",
        option = RegexOption.IGNORE_CASE
    )

    fun match(transcript: String): WakeMatchResult {
        val cleaned = transcript.trim()
        if (cleaned.isEmpty()) return WakeMatchResult(matched = false)
        val match = WAKE_REGEX.find(cleaned) ?: return WakeMatchResult(matched = false)
        val trailing = match.groupValues.getOrNull(1).orEmpty().trim()
        return WakeMatchResult(
            matched = true,
            commandAfterWakeWord = trailing
        )
    }
}
