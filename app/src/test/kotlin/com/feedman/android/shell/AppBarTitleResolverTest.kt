package com.feedman.android.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #31 / Req 1.1〜1.5 単体テスト。
 *
 * - Req 1.1: timeline ルート時に「すべての新着」相当のタイトルを返す
 * - Req 1.2: starred ルート時に「お気に入り」相当のタイトルを返す
 * - Req 1.3: feed/{feedId} ルート時に feedId に対応するフィード名を返す
 * - Req 1.3 異常系: feedTitleLookup が null を返したらフォールバック文字列を採用
 * - Req 1.4: サブタイトル定義のないルートは subtitle=null
 * - Req 1.5: ルート遷移したら resolve 結果も切り替わる（同一関数を別ルートで呼び返値変化）
 */
class AppBarTitleResolverTest {

    private val strings = AppBarStrings(
        timelineTitle = "すべての新着",
        starredTitle = "お気に入り",
        searchTitle = "検索",
        feedFallbackTitle = "フィード",
    )

    @Test
    fun `timeline ルートはタイトルにすべての新着を返す_Req 1_1`() {
        // Arrange / Act
        val result = resolveAppBarTitle(
            routeId = AppRoute.ROUTE_TIMELINE,
            strings = strings,
        )
        // Assert
        assertEquals("すべての新着", result.title)
        assertNull(result.subtitle)
    }

    @Test
    fun `starred ルートはタイトルにお気に入りを返す_Req 1_2`() {
        // Arrange / Act
        val result = resolveAppBarTitle(
            routeId = AppRoute.ROUTE_STARRED,
            strings = strings,
        )
        // Assert
        assertEquals("お気に入り", result.title)
        assertNull(result.subtitle)
    }

    @Test
    fun `feed ルートでは feedId に対応するフィード名をタイトルとして返す_Req 1_3`() {
        // Arrange
        val lookup: (String) -> String? = { id -> if (id == "f1") "Publickey" else null }
        // Act
        val result = resolveAppBarTitle(
            routeId = "feed/f1",
            feedTitleLookup = lookup,
            strings = strings,
        )
        // Assert
        assertEquals("Publickey", result.title)
    }

    @Test
    fun `feed ルートでフィード名が未解決の場合はフォールバック文字列を返す_Req 1_3_異常系`() {
        // Arrange
        val lookup: (String) -> String? = { null }
        // Act
        val result = resolveAppBarTitle(
            routeId = "feed/unknown-id",
            feedTitleLookup = lookup,
            strings = strings,
        )
        // Assert
        assertEquals("フィード", result.title)
    }

    @Test
    fun `feed ルートでテンプレートそのままが渡されたときも未解決として扱う_Req 1_3_境界`() {
        // Arrange
        val lookup: (String) -> String? = { id -> "解決された:$id" }
        // Act: NavController が currentRoute としてテンプレートを返した場合
        val result = resolveAppBarTitle(
            routeId = "feed/{feedId}",
            feedTitleLookup = lookup,
            strings = strings,
        )
        // Assert: lookup を呼ばずフォールバックに倒れる
        assertEquals("フィード", result.title)
    }

    @Test
    fun `search ルートでは検索のタイトルを返す_Req 1_5`() {
        // Arrange / Act
        val result = resolveAppBarTitle(
            routeId = AppRoute.ROUTE_SEARCH,
            strings = strings,
        )
        // Assert
        assertEquals("検索", result.title)
    }

    @Test
    fun `想定外ルート ID は安全側でタイムラインタイトルにフォールバックする_Req 1_5_境界`() {
        // Arrange / Act
        val result = resolveAppBarTitle(
            routeId = "unknown-route",
            strings = strings,
        )
        // Assert
        assertEquals("すべての新着", result.title)
        assertNull(result.subtitle)
    }

    @Test
    fun `ルートが timeline から starred に切り替わると結果も切り替わる_Req 1_5`() {
        // Arrange / Act
        val a = resolveAppBarTitle(routeId = AppRoute.ROUTE_TIMELINE, strings = strings)
        val b = resolveAppBarTitle(routeId = AppRoute.ROUTE_STARRED, strings = strings)
        // Assert
        assertEquals("すべての新着", a.title)
        assertEquals("お気に入り", b.title)
    }

    @Test
    fun `extractFeedIdFromRouteId は feed prefix から feedId 部分を返す_Req 1_3`() {
        // Arrange / Act
        val id = extractFeedIdFromRouteId("feed/abc-123")
        // Assert
        assertEquals("abc-123", id)
    }

    @Test
    fun `extractFeedIdFromRouteId はテンプレートに対し null を返す_Req 1_3_境界`() {
        // Arrange / Act
        val id = extractFeedIdFromRouteId("feed/{feedId}")
        // Assert
        assertNull(id)
    }

    @Test
    fun `extractFeedIdFromRouteId は無関係 prefix に対し null を返す_異常系`() {
        // Arrange / Act
        val id = extractFeedIdFromRouteId("timeline")
        // Assert
        assertNull(id)
    }

    @Test
    fun `extractFeedIdFromRouteId は空 feedId に対し null を返す_Req 1_3_境界`() {
        // Arrange / Act
        val id = extractFeedIdFromRouteId("feed/")
        // Assert
        assertNull(id)
    }
}
