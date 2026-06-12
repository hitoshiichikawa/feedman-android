package com.feedman.android.feature.feed

import com.feedman.android.core.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [resolveBanner] / [FeedStatusBanner] の純粋ロジックテスト（Issue #41 Req 3.1 / 3.2 / 3.3 /
 * 3.4 / 3.6 / 4.1 / 4.2 / 4.3）。
 */
class FeedStatusBannerTest {

    @Test
    fun `subscription が null のとき Hidden を返す_Req 4_3 取得未完了`() {
        // Act
        val banner = resolveBanner(subscription = null, resumeInProgress = false)

        // Assert
        assertEquals(FeedStatusBanner.Hidden, banner)
    }

    @Test
    fun `feed_status が active のとき Hidden を返す_Req 3_4`() {
        // Arrange
        val sub = subscription(status = "active", errorMessage = null)

        // Act
        val banner = resolveBanner(subscription = sub, resumeInProgress = false)

        // Assert
        assertEquals(FeedStatusBanner.Hidden, banner)
    }

    @Test
    fun `feed_status が stopped のとき Visible STOPPED を返す_Req 3_1 3_2`() {
        // Arrange
        val sub = subscription(status = "stopped", errorMessage = "手動停止")

        // Act
        val banner = resolveBanner(subscription = sub, resumeInProgress = false)

        // Assert
        assertTrue(banner is FeedStatusBanner.Visible)
        val v = banner as FeedStatusBanner.Visible
        assertEquals(FeedStatusBanner.Kind.STOPPED, v.kind)
        assertEquals("手動停止", v.message)
        assertEquals(false, v.resumeInProgress)
    }

    @Test
    fun `feed_status が error のとき Visible ERROR を返す_Req 3_1 3_2`() {
        // Arrange
        val sub = subscription(status = "error", errorMessage = "404 Not Found")

        // Act
        val banner = resolveBanner(subscription = sub, resumeInProgress = false)

        // Assert
        assertTrue(banner is FeedStatusBanner.Visible)
        val v = banner as FeedStatusBanner.Visible
        assertEquals(FeedStatusBanner.Kind.ERROR, v.kind)
        assertEquals("404 Not Found", v.message)
    }

    @Test
    fun `error_message が null のとき Visible の message も null になる_Req 3_3`() {
        // Arrange
        val sub = subscription(status = "error", errorMessage = null)

        // Act
        val banner = resolveBanner(subscription = sub, resumeInProgress = false)

        // Assert
        val v = banner as FeedStatusBanner.Visible
        assertEquals(null, v.message)
        // fallbackMessage 識別子は ERROR 側を選ぶ
        assertEquals(FeedStatusBanner.FallbackMessage.ERROR_DEFAULT, v.fallbackMessage)
    }

    @Test
    fun `error_message が空文字のとき Visible の message を null として扱う_Req 3_3`() {
        // Arrange
        val sub = subscription(status = "stopped", errorMessage = "   ")

        // Act
        val banner = resolveBanner(subscription = sub, resumeInProgress = false)

        // Assert
        val v = banner as FeedStatusBanner.Visible
        assertEquals(null, v.message)
        assertEquals(FeedStatusBanner.FallbackMessage.STOPPED_DEFAULT, v.fallbackMessage)
    }

    @Test
    fun `resumeInProgress true が Visible へ伝搬する_Req 3_6`() {
        // Arrange
        val sub = subscription(status = "error", errorMessage = "失敗")

        // Act
        val banner = resolveBanner(subscription = sub, resumeInProgress = true)

        // Assert
        val v = banner as FeedStatusBanner.Visible
        assertEquals(true, v.resumeInProgress)
    }

    @Test
    fun `未知の feed_status は防衛的に Hidden`() {
        // Arrange
        val sub = subscription(status = "unknown-future-state", errorMessage = "x")

        // Act
        val banner = resolveBanner(subscription = sub, resumeInProgress = false)

        // Assert
        assertEquals(FeedStatusBanner.Hidden, banner)
    }

    @Test
    fun `FeedFilter は API の FeedItemFilter へ 1対1 で射影される_Req 1_2 2_3`() {
        assertEquals(
            com.feedman.android.core.data.FeedItemFilter.ALL,
            FeedFilter.ALL.toFeedItemFilter(),
        )
        assertEquals(
            com.feedman.android.core.data.FeedItemFilter.UNREAD,
            FeedFilter.UNREAD.toFeedItemFilter(),
        )
        assertEquals(
            com.feedman.android.core.data.FeedItemFilter.STARRED,
            FeedFilter.STARRED.toFeedItemFilter(),
        )
        // Req 2.2: 初期表示時のデフォルト
        assertEquals(FeedFilter.ALL, FeedFilter.DEFAULT)
    }

    private fun subscription(status: String, errorMessage: String?): Subscription =
        Subscription(
            id = "sub-1",
            userId = "u",
            feedId = "feed-1",
            feedTitle = "Sample",
            feedUrl = "https://example.com",
            faviconUrl = null,
            fetchIntervalMinutes = 60,
            feedStatus = status,
            errorMessage = errorMessage,
            unreadCount = 0,
            createdAt = "2026-01-01T00:00:00Z",
        )
}
