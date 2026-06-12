package com.feedman.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Subscription] の decode 検証（Req 1.1 / 1.4 / 2.4 / 3.1 / 3.2）。
 */
class SubscriptionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes active subscription with data URL favicon and null error_message`() {
        // Arrange
        val payload = FixtureLoader.load("subscription_active.json")

        // Act
        val sub = json.decodeFromString(Subscription.serializer(), payload)

        // Assert
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3SU", sub.id)
        assertEquals("Feedman Dev Blog", sub.feedTitle)
        assertEquals("https://example.com/feed.xml", sub.feedUrl)
        assertNotNull(sub.faviconUrl)
        assertEquals(60, sub.fetchIntervalMinutes)
        assertEquals("active", sub.feedStatus)
        assertNull(sub.errorMessage)
        assertEquals(12, sub.unreadCount)
        assertEquals("2026-01-15T10:00:00Z", sub.createdAt)
    }

    @Test
    fun `decodes error subscription with null favicon and non-null error_message`() {
        // Arrange
        val payload = FixtureLoader.load("subscription_error.json")

        // Act
        val sub = json.decodeFromString(Subscription.serializer(), payload)

        // Assert
        assertNull(sub.faviconUrl)
        assertEquals("error", sub.feedStatus)
        assertEquals("HTTP 503 Service Unavailable", sub.errorMessage)
        assertEquals(180, sub.fetchIntervalMinutes)
        assertEquals(0, sub.unreadCount)
    }
}
