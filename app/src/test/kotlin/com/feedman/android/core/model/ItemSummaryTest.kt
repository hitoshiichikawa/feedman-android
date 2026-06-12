package com.feedman.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fixture JSON を [ItemSummary] / [ItemDetail] / [StarredItemSummary] へ decode できることを
 * 検証する単体テスト群（Req 1.1 / 1.4 / 3.1 / 3.5 / 3.6 / 3.7）。
 */
class ItemSummaryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes ItemSummary with hatebu_fetched_at populated (Req 3-1, 3-6)`() {
        // Arrange
        val payload = FixtureLoader.load("item_summary.json")

        // Act
        val item = json.decodeFromString(ItemSummary.serializer(), payload)

        // Assert
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3D1", item.id)
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3FE", item.feedId)
        assertEquals("Kotlin 2.0 のリリース", item.title)
        assertEquals("2026-06-08T12:00:00Z", item.publishedAt)
        assertEquals(128, item.hatebuCount)
        assertNotNull(item.hatebuFetchedAt)
        // Req 3.6: RFC3339 文字列は欠落なく保持される
        assertEquals("2026-06-08T13:00:00Z", item.hatebuFetchedAt)
    }

    @Test
    fun `decodes ItemSummary when hatebu_fetched_at is missing (Req 3-7)`() {
        // Arrange
        val payload = FixtureLoader.load("item_summary_no_hatebu_fetched_at.json")

        // Act
        val item = json.decodeFromString(ItemSummary.serializer(), payload)

        // Assert
        // Req 3.7: nullable と宣言されたフィールドが欠落していれば null として扱う
        assertNull(item.hatebuFetchedAt)
        assertEquals("はてブ未取得な記事", item.title)
        assertTrue(item.isDateEstimated)
    }

    @Test
    fun `decodes ItemSummary even when payload carries unknown keys (Req 3-5)`() {
        // Arrange
        val payload = FixtureLoader.load("item_summary_with_unknown_keys.json")

        // Act
        val item = json.decodeFromString(ItemSummary.serializer(), payload)

        // Assert
        // Req 3.5: 未知キーが含まれていても既知フィールドは値を保持する
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3UN", item.id)
        assertEquals("未知のキーを含む記事", item.title)
        assertEquals(99, item.hatebuCount)
        assertEquals("2026-06-04T13:00:00Z", item.hatebuFetchedAt)
    }

    @Test
    fun `decodes ItemDetail preserving content and author (Req 1-1, 1-4)`() {
        // Arrange
        val payload = FixtureLoader.load("item_detail.json")

        // Act
        val detail = json.decodeFromString(ItemDetail.serializer(), payload)

        // Assert
        assertEquals("詳細記事のサンプル", detail.title)
        assertEquals("<p>これは sanitized HTML 本文のサンプルです。</p>", detail.content)
        assertEquals("Feedman Test", detail.author)
        assertEquals("2026-06-07T11:00:00Z", detail.hatebuFetchedAt)
    }

    @Test
    fun `decodes StarredItemSummary preserving feed_title (Req 1-1, 1-4)`() {
        // Arrange
        val payload = FixtureLoader.load("starred_item.json")

        // Act
        val starred = json.decodeFromString(StarredItemSummary.serializer(), payload)

        // Assert
        assertEquals("Android Developers", starred.feedTitle)
        assertEquals("スター付き記事", starred.title)
        // null hatebu_fetched_at の場合
        assertNull(starred.hatebuFetchedAt)
        assertTrue(starred.isStarred)
    }
}
