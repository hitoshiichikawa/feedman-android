package com.feedman.android.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #29: ルート定義の単体テスト。
 *
 * - Req 1.1: 初期ルートが `timeline` であること（[AppRoute.Timeline.id] を初期表示で
 *   使うため、ID が `"timeline"` であることをここで固定検証する）。
 * - Req 1.2: 宣言ルートが 4 件かつ列挙が `timeline` / `feed/{feedId}` / `starred` /
 *   `search` であること。
 * - Req 1.3: `feed/{feedId}` ルートは `feedId` をパスパラメータとして展開できること
 *   （[AppRoute.Feed.path]）。
 */
class AppRouteTest {

    @Test
    fun `timeline ルート ID は 'timeline' である_Req 1_1`() {
        // Arrange / Act
        val id = AppRoute.Timeline.id
        // Assert
        assertEquals("timeline", id)
    }

    @Test
    fun `宣言ルートはちょうど 4 件で_仕様で定めた 4 ルートと一致する_Req 1_2`() {
        // Arrange
        val expected = listOf("timeline", "feed/{feedId}", "starred", "search")
        // Act
        val actual = AppRoute.declaredRouteIds
        // Assert
        assertEquals(4, actual.size)
        assertEquals(expected, actual)
    }

    @Test
    fun `Feed ルートテンプレートは 'feed-feedId' 形式である_Req 1_3`() {
        // Arrange / Act
        val template = AppRoute.Feed.id
        // Assert
        assertEquals("feed/{feedId}", template)
    }

    @Test
    fun `feed path は与えられた feedId を展開する_Req 1_3`() {
        // Arrange
        val feedId = "abc-123"
        // Act
        val path = AppRoute.Feed.path(feedId)
        // Assert
        assertEquals("feed/abc-123", path)
    }

    @Test
    fun `feed path は空 feedId に対し IllegalArgumentException を投げる_異常系_Req 1_3`() {
        // Arrange
        val emptyId = ""
        // Act / Assert
        assertThrows(IllegalArgumentException::class.java) {
            AppRoute.Feed.path(emptyId)
        }
    }

    @Test
    fun `記事詳細 - フィード登録 - 購読設定 - アカウントはルートに含まれない_Req 1_5`() {
        // Arrange
        val forbiddenIds = listOf(
            "article",
            "register",
            "subscription",
            "account",
        )
        // Act
        val declared = AppRoute.declaredRouteIds
        // Assert
        forbiddenIds.forEach { forbidden ->
            assertTrue(
                "AppRoute.declaredRouteIds には $forbidden が含まれてはならない（Req 1.5）",
                declared.none { it == forbidden },
            )
        }
    }
}
