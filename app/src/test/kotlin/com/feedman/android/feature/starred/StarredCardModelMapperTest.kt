package com.feedman.android.feature.starred

import com.feedman.android.core.model.StarredItemSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StarredCardModelMapper] の純粋ロジック検証（Issue #46 Req 1.4 / NFR 2.3）。
 *
 * Composable 起動不要のため JVM 単体テストで完結する。
 */
class StarredCardModelMapperTest {

    @Test
    fun `feed_title はカードの feedTitle にそのまま転写される`() {
        // Arrange
        val item = starredItem(id = "x", feedTitle = "Android Developers")

        // Act
        val card = StarredCardModelMapper.toCardModel(item)

        // Assert (Req 1.4)
        assertEquals("Android Developers", card.feedTitle)
    }

    @Test
    fun `is_read と is_starred はサーバー値をそのまま反映する`() {
        // Arrange: is_starred は API 上は常に true で返るが、転写ロジック自体は等価変換であることを担保
        val starredRead = starredItem(id = "r", isRead = true, isStarred = true)

        // Act
        val card = StarredCardModelMapper.toCardModel(starredRead)

        // Assert
        assertEquals(true, card.isRead)
        assertEquals(true, card.isStarred)
    }

    @Test
    fun `favicon URL を持たないため faviconValue は null になる`() {
        // Arrange
        val item = starredItem(id = "n")

        // Act
        val card = StarredCardModelMapper.toCardModel(item)

        // Assert
        assertNull(
            "StarredItemSummary は favicon URL を持たない → faviconValue=null（レターアバター fallback）",
            card.faviconValue,
        )
    }

    @Test
    fun `published_at_isDateEstimated_hatebu などの中立フィールドが等価転写される`() {
        // Arrange
        val item = starredItem(
            id = "z",
            publishedAt = "2026-06-12T11:30:00Z",
            isDateEstimated = true,
            hatebuCount = 42,
            hatebuFetchedAt = "2026-06-12T12:00:00Z",
        )

        // Act
        val card = StarredCardModelMapper.toCardModel(item)

        // Assert
        assertEquals("2026-06-12T11:30:00Z", card.publishedAtIso)
        assertEquals(true, card.isDateEstimated)
        assertEquals(42, card.hatebuCount)
        assertEquals("2026-06-12T12:00:00Z", card.hatebuFetchedAt)
    }

    @Test
    fun `link と summary も等価転写される`() {
        // Arrange
        val item = starredItem(
            id = "l",
            link = "https://example.com/article",
            summary = "概要本文",
        )

        // Act
        val card = StarredCardModelMapper.toCardModel(item)

        // Assert
        assertEquals("https://example.com/article", card.link)
        assertEquals("概要本文", card.summary)
        assertEquals("l", card.id)
    }

    @Test
    fun `空文字列タイトルや null hatebu_fetched_at も例外なく転写される_境界値`() {
        // Arrange: 境界値（NFR 2.3 の異常系/境界値カバレッジ）
        val item = starredItem(
            id = "e",
            title = "",
            hatebuFetchedAt = null,
        )

        // Act
        val card = StarredCardModelMapper.toCardModel(item)

        // Assert
        assertTrue("空タイトルでも例外を投げない", card.title.isEmpty())
        assertNull(card.hatebuFetchedAt)
    }

    private fun starredItem(
        id: String,
        feedId: String = "feed-x",
        feedTitle: String = "Feed X",
        title: String = "Title",
        link: String = "https://example.com/$id",
        summary: String = "",
        publishedAt: String = "2026-06-12T11:30:00Z",
        isDateEstimated: Boolean = false,
        isRead: Boolean = false,
        isStarred: Boolean = true,
        hatebuCount: Int = 0,
        hatebuFetchedAt: String? = null,
    ): StarredItemSummary = StarredItemSummary(
        id = id,
        feedId = feedId,
        title = title,
        link = link,
        summary = summary,
        publishedAt = publishedAt,
        isDateEstimated = isDateEstimated,
        isRead = isRead,
        isStarred = isStarred,
        hatebuCount = hatebuCount,
        hatebuFetchedAt = hatebuFetchedAt,
        feedTitle = feedTitle,
    )
}
