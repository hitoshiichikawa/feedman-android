package com.feedman.android.feature.timeline

import com.feedman.android.core.model.CrossFeedItem
import com.feedman.android.core.ui.ArticleCardModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TimelineCardModelMapper] (Issue #33 Req 1.1〜1.7, 2.1, 2.2).
 *
 * カード描画モデルへの変換は純粋関数として、フィード名 / favicon / 概要 / 既読フラグ /
 * はてブ数の各要素が抜け漏れなく `ArticleCardModel` に転写されることを検証する。
 *
 * cross-feed API レスポンスには `hatebu_fetched_at` が含まれないため（SPEC §4.2:
 * CrossFeedItem には未定義）、はてブ数表示は常に「未取得（"−"）」ではなく数値表示と
 * するために [TimelineCardModelMapper.toCardModel] が固定の RFC3339 値を埋めて
 * `ArticleCardModel.hatebuFetchedAt` に渡す挙動を担保する。
 */
class TimelineCardModelMapperTest {

    private val sampleItem = CrossFeedItem(
        id = "item-1",
        feedId = "feed-1",
        feedTitle = "Hacker News",
        feedFaviconUrl = "data:image/png;base64,AAAA",
        title = "Sample title",
        link = "https://example.com/a",
        summary = "Sample summary",
        publishedAt = "2026-06-12T11:30:00Z",
        isDateEstimated = false,
        isRead = false,
        isStarred = false,
        hatebuCount = 42,
    )

    @Test
    fun `フィード名と favicon と相対日時の元値が ArticleCardModel に転写される`() {
        // Arrange / Act
        val model = TimelineCardModelMapper.toCardModel(sampleItem)

        // Assert
        assertEquals("item-1", model.id)
        assertEquals("Hacker News", model.feedTitle)
        assertEquals("data:image/png;base64,AAAA", model.faviconValue)
        assertEquals("2026-06-12T11:30:00Z", model.publishedAtIso)
        assertEquals(false, model.isDateEstimated)
    }

    @Test
    fun `タイトルと概要が転写される`() {
        // Arrange / Act
        val model = TimelineCardModelMapper.toCardModel(sampleItem)

        // Assert
        assertEquals("Sample title", model.title)
        assertEquals("Sample summary", model.summary)
    }

    @Test
    fun `summary が空文字列のときも空文字列のまま転写される（カード側で行非表示判定）`() {
        // Arrange
        val item = sampleItem.copy(summary = "")

        // Act
        val model = TimelineCardModelMapper.toCardModel(item)

        // Assert: ArticleCard 側で `summary.isNotEmpty()` 判定する仕様（Req 1.5）に従い、
        // mapper は空文字列をそのまま通す
        assertEquals("", model.summary)
    }

    @Test
    fun `is_read true は ArticleCardModel に伝播し既読不透明度の根拠になる`() {
        // Arrange
        val item = sampleItem.copy(isRead = true)

        // Act
        val model = TimelineCardModelMapper.toCardModel(item)

        // Assert (Req 2.1)
        assertEquals(true, model.isRead)
    }

    @Test
    fun `is_read false は ArticleCardModel に伝播し未読不透明度の根拠になる`() {
        // Arrange
        val item = sampleItem.copy(isRead = false)

        // Act
        val model = TimelineCardModelMapper.toCardModel(item)

        // Assert (Req 2.2)
        assertEquals(false, model.isRead)
    }

    @Test
    fun `is_starred はスタートグルの初期状態として転写される`() {
        // Arrange
        val item = sampleItem.copy(isStarred = true)

        // Act
        val model = TimelineCardModelMapper.toCardModel(item)

        // Assert (Req 1.7)
        assertEquals(true, model.isStarred)
    }

    @Test
    fun `hatebuCount が転写される`() {
        // Arrange
        val item = sampleItem.copy(hatebuCount = 250)

        // Act
        val model = TimelineCardModelMapper.toCardModel(item)

        // Assert (Req 1.6)
        assertEquals(250, model.hatebuCount)
    }

    @Test
    fun `hatebuFetchedAt は CrossFeedItem に無いため publishedAt が代用される`() {
        // Arrange / Act
        val model = TimelineCardModelMapper.toCardModel(sampleItem)

        // Assert: HatebuBadge は hatebuFetchedAt が null のときに「−」を表示するため
        // （HatebuLogic / Issue #27 Req 2.2）、cross-feed では publishedAt を流用して
        // 「取得済み（数値表示）」相当の挙動を取らせる
        assertEquals("2026-06-12T11:30:00Z", model.hatebuFetchedAt)
    }

    @Test
    fun `is_date_estimated true は相対日時の(推定)表示根拠として転写される`() {
        // Arrange
        val item = sampleItem.copy(isDateEstimated = true)

        // Act
        val model = TimelineCardModelMapper.toCardModel(item)

        // Assert (Req 1.2 / RelativeTimeFormatter Req 3.5 連動)
        assertEquals(true, model.isDateEstimated)
    }

    @Test
    fun `feedFaviconUrl が null のときも null のまま転写される（Favicon 側でレターアバター fallback）`() {
        // Arrange
        val item = sampleItem.copy(feedFaviconUrl = null)

        // Act
        val model = TimelineCardModelMapper.toCardModel(item)

        // Assert (Req 1.1 — Favicon が null 時の挙動は Issue #26 で担保済み)
        assertEquals(null, model.faviconValue)
    }

    @Test
    fun `toMockCardModel は MockTimelineItem を ArticleCardModel に変換する`() {
        // Arrange
        val mockItem = com.feedman.android.core.model.MockTimelineItem(
            id = "mock-1",
            title = "Mock title",
            feedName = "Mock Feed",
            publishedAt = "10 分前",
        )
        val now = "2026-06-12T12:00:00Z"

        // Act
        val model = TimelineCardModelMapper.toMockCardModel(mockItem, fallbackPublishedAtIso = now)

        // Assert: mockMode の表示確認に最低限必要なフィールドだけ転写される（NFR: mockMode 動作を壊さない）
        assertEquals("mock-1", model.id)
        assertEquals("Mock title", model.title)
        assertEquals("Mock Feed", model.feedTitle)
        assertEquals(null, model.faviconValue)
        assertEquals(now, model.publishedAtIso)
        assertEquals(false, model.isRead)
        assertEquals(false, model.isStarred)
        assertEquals(0, model.hatebuCount)
    }
}
