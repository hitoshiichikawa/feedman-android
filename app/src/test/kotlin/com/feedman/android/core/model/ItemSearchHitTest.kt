package com.feedman.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ItemSearchHit] の decode 検証（Req 1.5 / 3.3）。
 *
 * ItemSearchHit は ItemSummary とは差分があり、`hatebu_fetched_at` を含まず、
 * `feed_title` を含み、`favicon_url` と `published_at` が nullable な点を確認する。
 */
class ItemSearchHitTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes ItemSearchHit with data URL favicon and full fields (Req 3-3)`() {
        // Arrange
        val payload = FixtureLoader.load("item_search_hit_with_favicon.json")

        // Act
        val hit = json.decodeFromString(ItemSearchHit.serializer(), payload)

        // Assert
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3D5", hit.id)
        assertEquals("Search Source Feed", hit.feedTitle)
        assertNotNull(hit.faviconUrl)
        assertTrue(hit.faviconUrl!!.startsWith("data:image/png;base64,"))
        // published_at は nullable だが値が入っているケース
        assertEquals("2026-06-05T19:00:00Z", hit.publishedAt)
        assertFalse(hit.isDateEstimated)
        assertEquals(15, hit.hatebuCount)
        assertFalse(hit.isRead)
        assertFalse(hit.isStarred)
    }

    @Test
    fun `decodes ItemSearchHit with null published_at and null favicon_url (Req 1-5, 3-3)`() {
        // Arrange
        val payload = FixtureLoader.load("item_search_hit_nullable_fields.json")

        // Act
        val hit = json.decodeFromString(ItemSearchHit.serializer(), payload)

        // Assert
        // Req 1.5 / 3.3: published_at と favicon_url がともに null でも decode 成功
        assertNull(hit.publishedAt)
        assertNull(hit.faviconUrl)
        assertEquals("Unknown Source", hit.feedTitle)
        assertTrue(hit.isDateEstimated)
        assertTrue(hit.isRead)
    }
}
