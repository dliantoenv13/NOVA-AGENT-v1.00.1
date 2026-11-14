package com.example

import com.example.voice.WakeWordMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordMatcherTest {

    @Test
    fun matchesExactHiNova() {
        val result = WakeWordMatcher.match("Hi Nova")
        assertTrue(result.matched)
        assertEquals("", result.commandAfterWakeWord)
    }

    @Test
    fun matchesIndonesianPhoneticHaiNovaAndExtractsCommand() {
        val result = WakeWordMatcher.match("hai nova jelaskan tentang lubang hitam")
        assertTrue(result.matched)
        assertEquals("jelaskan tentang lubang hitam", result.commandAfterWakeWord)
    }

    @Test
    fun matchesWithPunctuation() {
        val result = WakeWordMatcher.match("Hi, Nova! What is the weather today")
        assertTrue(result.matched)
        assertEquals("What is the weather today", result.commandAfterWakeWord)
    }

    @Test
    fun doesNotMatchUnrelatedPhrases() {
        assertFalse(WakeWordMatcher.match("Halo dunia").matched)
        assertFalse(WakeWordMatcher.match("Nova buka kamera").matched)
        assertFalse(WakeWordMatcher.match("Hi Enova").matched)
    }
}
