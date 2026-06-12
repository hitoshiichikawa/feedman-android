package com.feedman.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppConfig] (Req 5.1, 5.2).
 *
 * The class itself is a trivial data class; these tests assert the contract that the
 * skeleton relies on (baseUrl / mockMode round-trip preserved exactly) so that any
 * future refactor that changes equality semantics is caught.
 */
class AppConfigTest {

    @Test
    fun `preserves baseUrl and mockMode round-trip via data class equality`() {
        val a = AppConfig(baseUrl = "https://api.example.com", mockMode = true)
        val b = AppConfig(baseUrl = "https://api.example.com", mockMode = true)
        assertEquals(a, b)
        assertEquals("https://api.example.com", a.baseUrl)
        assertTrue(a.mockMode)
    }

    @Test
    fun `mockMode false defaults are not equal to mockMode true configs`() {
        val off = AppConfig(baseUrl = "https://x", mockMode = false)
        val on = AppConfig(baseUrl = "https://x", mockMode = true)
        assertNotEquals(off, on)
        assertFalse(off.mockMode)
    }
}
