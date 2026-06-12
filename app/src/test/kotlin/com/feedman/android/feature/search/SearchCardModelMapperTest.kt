package com.feedman.android.feature.search

import com.feedman.android.core.model.ItemSearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SearchCardModelMapper] の AC 単位検証（Issue #47 Req 4 / NFR 2.2）。
 *
 * [ItemSearchHit] の差分（`hatebu_fetched_at` 無し / `feed_title` 有り /
 * `favicon_url` と `published_at` が nullable）が [com.feedman.android.core.ui.ArticleCardModel]
 * へ正しく射影されることを検証する。
 */
class SearchCardModelMapperTest {

    private fun newHit(
        id: String = "hit-1",
        feedId: String = "feed-1",
        title: String = "横断検索のヒット",
        link: String = "https://example.com/hit",
        summary: String = "summary",
        publishedAt: String? = "2026-06-11T09:00:00Z",
        isDateEstimated: Boolean = false,
        hatebuCount: Int = 7,
        feedTitle: String = "Search Source Feed",
        faviconUrl: String? = "data:image/png;base64,AAA",
        isRead: Boolean = false,
        isStarred: Boolean = false,
    ): ItemSearchHit = ItemSearchHit(
        id = id,
        feedId = feedId,
        title = title,
        link = link,
        summary = summary,
        publishedAt = publishedAt,
        isDateEstimated = isDateEstimated,
        hatebuCount = hatebuCount,
        feedTitle = feedTitle,
        faviconUrl = faviconUrl,
        isRead = isRead,
        isStarred = isStarred,
    )

    // ---- Req 4.2 / 4.3: feed_title をソース表示に、favicon_url を data URL として伝達

    @Test
    fun `toCardModel maps feed_title to feedTitle and favicon_url verbatim`() {
        // Arrange
        val hit = newHit(
            feedTitle = "Android Developers",
            faviconUrl = "data:image/png;base64,XYZ",
        )

        // Act
        val card = SearchCardModelMapper.toCardModel(hit)

        // Assert
        assertEquals("Req 4.2: feed_title をソース表示に", "Android Developers", card.feedTitle)
        assertEquals(
            "Req 4.3: favicon_url（data URL）をそのまま faviconValue に渡す",
            "data:image/png;base64,XYZ",
            card.faviconValue,
        )
    }

    // ---- Req 4.4: favicon_url が null ならレターアバター fallback（faviconValue=null）---

    @Test
    fun `toCardModel passes null faviconValue when favicon_url is null`() {
        // Arrange
        val hit = newHit(faviconUrl = null)

        // Act
        val card = SearchCardModelMapper.toCardModel(hit)

        // Assert
        assertNull(
            "Req 4.4: favicon_url が null のとき faviconValue は null（Favicon 側でレターアバター fallback）",
            card.faviconValue,
        )
    }

    // ---- Req 4.5: published_at が非 null のとき RFC3339 文字列をそのまま伝達 ---

    @Test
    fun `toCardModel propagates non-null published_at verbatim`() {
        // Arrange
        val hit = newHit(publishedAt = "2026-06-11T07:00:00Z")

        // Act
        val card = SearchCardModelMapper.toCardModel(hit)

        // Assert
        assertEquals(
            "Req 4.5: published_at をそのまま publishedAtIso に伝達する",
            "2026-06-11T07:00:00Z",
            card.publishedAtIso,
        )
    }

    // ---- Req 4.6: published_at が null のとき不明日時を示す代替表現に正規化 ---

    @Test
    fun `toCardModel normalises null published_at to UNKNOWN_PUBLISHED_AT`() {
        // Arrange
        val hit = newHit(publishedAt = null)

        // Act
        val card = SearchCardModelMapper.toCardModel(hit)

        // Assert
        assertEquals(
            "Req 4.6: published_at が null のとき UNKNOWN_PUBLISHED_AT に正規化し、描画側 fallback に委ねる",
            SearchCardModelMapper.UNKNOWN_PUBLISHED_AT,
            card.publishedAtIso,
        )
    }

    // ---- Req 4.7 / NFR 2.2: hatebu_count を伝達、hatebu_fetched_at は null ---

    @Test
    fun `toCardModel propagates hatebu_count and sets hatebuFetchedAt to null`() {
        // Arrange
        val hit = newHit(hatebuCount = 42)

        // Act
        val card = SearchCardModelMapper.toCardModel(hit)

        // Assert
        assertEquals("Req 4.7: hatebu_count をそのまま伝達", 42, card.hatebuCount)
        assertNull(
            "Req 4.7: ItemSearchHit は hatebu_fetched_at を持たないため null（HatebuBadge は count のみ表示）",
            card.hatebuFetchedAt,
        )
    }

    // ---- Req 4.8 / 4.1: スター状態・ID・タイトル等を伝達 ---------------------

    @Test
    fun `toCardModel propagates id title summary link star and read flags`() {
        // Arrange
        val hit = newHit(
            id = "id-99",
            title = "title-99",
            summary = "sum-99",
            link = "https://example.com/99",
            isStarred = true,
            isRead = true,
        )

        // Act
        val card = SearchCardModelMapper.toCardModel(hit)

        // Assert
        assertEquals("id-99", card.id)
        assertEquals("title-99", card.title)
        assertEquals("sum-99", card.summary)
        assertEquals("https://example.com/99", card.link)
        assertTrue("Req 4.8: isStarred を伝達", card.isStarred)
        assertTrue(card.isRead)
    }

    // ---- NFR 2.2 (boundary): すべて非 null の正常パスを 1 ケース確認 -------

    @Test
    fun `toCardModel maps a fully populated hit without any fallback`() {
        // Arrange
        val hit = newHit(
            publishedAt = "2026-06-11T05:00:00Z",
            faviconUrl = "data:image/png;base64,FULL",
        )

        // Act
        val card = SearchCardModelMapper.toCardModel(hit)

        // Assert
        assertEquals("2026-06-11T05:00:00Z", card.publishedAtIso)
        assertEquals("data:image/png;base64,FULL", card.faviconValue)
        assertFalse(card.isDateEstimated)
    }
}
