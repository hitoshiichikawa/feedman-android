package com.feedman.android.shell

import com.feedman.android.core.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DrawerFeedRow] / [FeedStatusIcon] 純粋ロジックの単体テスト
 * （Issue #30 / Req 1.1.1, 1.1.2, 2.1, 2.2, 2.3）。
 *
 * UI 表示判定（未読バッジ表示 / 状態アイコン選択）を Composable 起動なしで検証する。
 */
class DrawerFeedRowTest {

    @Test
    fun `unread が 1 以上のときは未読バッジを表示する_Req 1_1_1`() {
        // Arrange / Act / Assert
        assertTrue(DrawerFeedRow.shouldShowUnreadBadge(1))
        assertTrue(DrawerFeedRow.shouldShowUnreadBadge(99))
    }

    @Test
    fun `unread が 0 のときは未読バッジを非表示にする_Req 1_1_2`() {
        // Arrange / Act / Assert
        assertFalse(DrawerFeedRow.shouldShowUnreadBadge(0))
    }

    @Test
    fun `unread が負の値（境界値）でも未読バッジを表示しない`() {
        // Arrange / Act / Assert
        // SPEC §4.2 の unread_count は非負だが、API 不整合時の防衛として 0 以下は非表示扱い
        assertFalse(DrawerFeedRow.shouldShowUnreadBadge(-1))
    }

    @Test
    fun `feed_status active のとき状態アイコンを表示しない_Req 2_3`() {
        // Arrange
        val status = "active"

        // Act
        val icon = FeedStatusIcon.from(status)

        // Assert
        assertEquals(FeedStatusIcon.None, icon)
    }

    @Test
    fun `feed_status stopped のとき停止アイコンを選択する_Req 2_1`() {
        // Arrange
        val status = "stopped"

        // Act
        val icon = FeedStatusIcon.from(status)

        // Assert
        assertEquals(FeedStatusIcon.Stopped, icon)
    }

    @Test
    fun `feed_status error のとき警告アイコンを選択する_Req 2_2`() {
        // Arrange
        val status = "error"

        // Act
        val icon = FeedStatusIcon.from(status)

        // Assert
        assertEquals(FeedStatusIcon.Error, icon)
    }

    @Test
    fun `未知の feed_status は安全側で None にフォールバックする`() {
        // Arrange
        val status = "unexpected_value"

        // Act
        val icon = FeedStatusIcon.from(status)

        // Assert
        assertEquals(
            "想定外の feed_status は状態アイコン非表示扱い（Req 2.3 と同じ挙動）",
            FeedStatusIcon.None,
            icon,
        )
    }

    @Test
    fun `Subscription から DrawerFeedRow への変換が必要なフィールドを正しく拾う_Req 1_2_3_3_5_4`() {
        // Arrange
        val subscription = Subscription(
            id = "s-x",
            userId = "u-x",
            feedId = "feed-42",
            feedTitle = "Sample Feed",
            feedUrl = "https://example.com/feed.xml",
            faviconUrl = "data:image/png;base64,AAAA",
            fetchIntervalMinutes = 30,
            feedStatus = "stopped",
            errorMessage = "停止中",
            unreadCount = 7,
            createdAt = "2025-05-01T00:00:00Z",
        )

        // Act
        val row = DrawerFeedRow.from(subscription)

        // Assert
        assertEquals("feed-42", row.feedId) // Req 3.3
        assertEquals("Sample Feed", row.title)
        assertEquals("data:image/png;base64,AAAA", row.faviconValue)
        assertEquals(7, row.unreadCount)
        assertEquals(FeedStatusIcon.Stopped, row.statusIcon) // Req 2.1
        assertTrue("unread=7 → 未読バッジ表示", row.showUnreadBadge) // Req 1.1.1
    }

    @Test
    fun `Subscription から DrawerFeedRow への変換で unread 0 のときは showUnreadBadge が false_Req 1_1_2`() {
        // Arrange
        val subscription = Subscription(
            id = "s-y",
            userId = "u-y",
            feedId = "feed-zero",
            feedTitle = "Empty Feed",
            feedUrl = "https://example.com/empty.xml",
            faviconUrl = null,
            fetchIntervalMinutes = 60,
            feedStatus = "active",
            errorMessage = null,
            unreadCount = 0,
            createdAt = "2025-05-01T00:00:00Z",
        )

        // Act
        val row = DrawerFeedRow.from(subscription)

        // Assert
        assertEquals(FeedStatusIcon.None, row.statusIcon) // Req 2.3
        assertFalse("unread=0 → 未読バッジ非表示", row.showUnreadBadge) // Req 1.1.2
    }
}
