package com.feedman.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ページ envelope（[Page] / [CrossFeedPage]）の decode 検証
 * （Req 1.2 / 1.3 / 2.2 / 2.3 / 3.1 / 3.4 / 3.6）。
 */
class PageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes Page with has_more true and a non-null next_cursor (Req 2-2, 3-1)`() {
        // Arrange
        val payload = FixtureLoader.load("item_summary_page_has_more.json")

        // Act
        val page = json.decodeFromString(Page.serializer(ItemSummary.serializer()), payload)

        // Assert
        assertEquals(2, page.items.size)
        assertTrue(page.hasMore)
        assertNotNull(page.nextCursor)
        assertEquals(
            "2026-06-10T08:00:00Z:01HGY8K9ZQ4N7TXVY1F8M9R3P2",
            page.nextCursor,
        )
        assertEquals("ページ 1 の記事 A", page.items[0].title)
    }

    @Test
    fun `decodes Page with has_more false and null next_cursor as terminal (Req 3-4)`() {
        // Arrange
        val payload = FixtureLoader.load("item_summary_page_terminal.json")

        // Act
        val page = json.decodeFromString(Page.serializer(ItemSummary.serializer()), payload)

        // Assert
        // Req 3.4: 終端ページが正しく表現できる
        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
        assertEquals(1, page.items.size)
        assertEquals("最後のページの記事", page.items[0].title)
    }

    @Test
    fun `decodes CrossFeedPage preserving since_time (Req 1-3, 2-3, 3-6)`() {
        // Arrange
        val payload = FixtureLoader.load("cross_feed_page.json")

        // Act
        val page = json.decodeFromString(CrossFeedPage.serializer(), payload)

        // Assert
        // Req 1.3 / 2.3: 横断新着 envelope は since_time を保持する
        assertEquals("2026-06-12T09:30:00Z", page.sinceTime)
        assertTrue(page.hasMore)
        assertNotNull(page.nextCursor)
        assertEquals(2, page.items.size)
        // CrossFeedItem の差分も保持されている
        assertEquals("Feedman Dev Blog", page.items[0].feedTitle)
        assertNull(page.items[1].feedFaviconUrl)
        assertTrue(page.items[1].isDateEstimated)
    }

    @Test
    fun `decodes CrossFeedPage terminal page with has_more false (Req 2-3, 3-4)`() {
        // Arrange
        val payload = FixtureLoader.load("cross_feed_page_terminal.json")

        // Act
        val page = json.decodeFromString(CrossFeedPage.serializer(), payload)

        // Assert
        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
        // since_time は終端ページでも保持される
        assertEquals("2026-06-12T09:30:00Z", page.sinceTime)
        assertEquals(1, page.items.size)
    }
}
