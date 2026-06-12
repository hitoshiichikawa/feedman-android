package com.feedman.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fixture JSON を [CrossFeedItem] へ decode できることを検証する単体テスト群
 * （Req 1.1 / 1.4 / 2.4 / 2.5 / 3.1 / 3.2 / 3.6）。
 *
 * `Json { ignoreUnknownKeys = true }` を model テスト内に閉じて利用する。共通 Json 設定の
 * 本格配置は Issue #17（core/network）の領分（NFR 1.1）。
 */
class CrossFeedItemTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a CrossFeedItem with data URL favicon and full fields (Req 3-1, 3-2, 3-6)`() {
        // Arrange
        val payload = FixtureLoader.load("cross_feed_item.json")

        // Act
        val item = json.decodeFromString(CrossFeedItem.serializer(), payload)

        // Assert
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3CA", item.id)
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3FE", item.feedId)
        assertEquals("Feedman Dev Blog", item.feedTitle)
        assertNotNull(item.feedFaviconUrl)
        assertTrue(
            "feed_favicon_url is expected to be preserved as a data URL string verbatim",
            item.feedFaviconUrl!!.startsWith("data:image/png;base64,"),
        )
        assertEquals("Compose Material 3 1.3 がリリース", item.title)
        assertEquals("https://example.com/blog/compose-1-3", item.link)
        assertEquals("Material 3 1.3 のリリースノート概要", item.summary)
        // Req 3.6: RFC3339 文字列は欠落なく保持される
        assertEquals("2026-06-10T09:00:00Z", item.publishedAt)
        // Req 2.5: is_date_estimated false ケース
        assertFalse(item.isDateEstimated)
        assertFalse(item.isRead)
        assertTrue(item.isStarred)
        assertEquals(42, item.hatebuCount)
    }

    @Test
    fun `decodes a CrossFeedItem with null favicon and estimated date (Req 2-4, 2-5, 3-2)`() {
        // Arrange
        val payload = FixtureLoader.load("cross_feed_item_null_favicon.json")

        // Act
        val item = json.decodeFromString(CrossFeedItem.serializer(), payload)

        // Assert
        // Req 3.2: feed_favicon_url が null の fixture でも decode は失敗しない
        assertNull(item.feedFaviconUrl)
        // Req 2.5: is_date_estimated true ケース
        assertTrue(item.isDateEstimated)
        assertEquals("Untitled Feed", item.feedTitle)
        assertEquals(0, item.hatebuCount)
    }
}
