package com.feedman.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [User] の decode 検証（Req 1.1 / 3.1 / 3.5）。
 */
class UserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes User and ignores unknown keys`() {
        // Arrange
        val payload = FixtureLoader.load("user.json")

        // Act
        val user = json.decodeFromString(User.serializer(), payload)

        // Assert
        // Req 3.5: 未知キー（display_name, created_at）があっても既知フィールドは保持される
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3US", user.id)
        assertEquals("alice@example.com", user.email)
    }
}
