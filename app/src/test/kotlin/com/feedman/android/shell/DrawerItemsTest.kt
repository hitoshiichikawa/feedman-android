package com.feedman.android.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #29: ドロワー項目（メイン / フッタ）の純粋データ定義に関するテスト。
 *
 * - Req 2.4 / 2.5: 「すべての新着」「お気に入り」の選択がそれぞれ `timeline` /
 *   `starred` ルートにマップされること。
 * - Req 4.1 / 4.2: v1 のフッタにキーワード通知エントリが含まれないこと（`enum`
 *   そのものに含めない構造で担保）。
 * - Req 4.3: フッタの表示／非表示はリスト 1 箇所（[drawerFooterItems]）で切り替え
 *   られること。
 */
class DrawerItemsTest {

    @Test
    fun `メイン項目 Timeline は timeline ルートに対応する_Req 2_4`() {
        // Arrange
        val item = DrawerMainItem.Timeline
        // Act
        val route = item.targetRouteId()
        // Assert
        assertEquals(AppRoute.ROUTE_TIMELINE, route)
        assertEquals("timeline", route)
    }

    @Test
    fun `メイン項目 Starred は starred ルートに対応する_Req 2_5`() {
        // Arrange
        val item = DrawerMainItem.Starred
        // Act
        val route = item.targetRouteId()
        // Assert
        assertEquals(AppRoute.ROUTE_STARRED, route)
        assertEquals("starred", route)
    }

    @Test
    fun `drawerMainItems はちょうど Timeline と Starred の 2 件である_Req 2_4_2_5`() {
        // Arrange / Act
        val items = drawerMainItems
        // Assert
        assertEquals(listOf(DrawerMainItem.Timeline, DrawerMainItem.Starred), items)
    }

    @Test
    fun `drawerFooterItems にキーワード通知エントリが含まれない_Req 4_1_4_2`() {
        // Arrange
        val footerNames = DrawerFooterItem.entries.map { it.name }
        // Act / Assert (構造上の不在を二重チェック)
        assertFalse(
            "DrawerFooterItem enum にキーワード通知（KeywordNotification）相当の値があってはならない（Req 4.1, 4.2）",
            footerNames.any { it.equals("KeywordNotification", ignoreCase = true) },
        )
        assertTrue(
            "drawerFooterItems の現在の表示順は Account → ThemeToggle に限定される（Req 4.1）",
            drawerFooterItems == listOf(
                DrawerFooterItem.Account,
                DrawerFooterItem.ThemeToggle,
            ),
        )
    }

    @Test
    fun `drawerFooterItems は DrawerFooterItem の全列挙の部分集合である_Req 4_3`() {
        // Arrange: 後続フェーズで通知導線を解禁する場合、エントリ追加箇所が
        //          drawerFooterItems の生成リテラル 1 箇所であることを構造的に保証する。
        // Act
        val footer = drawerFooterItems
        val all = DrawerFooterItem.entries
        // Assert: フッタは enum で型付けされており、表示順は drawerFooterItems の
        //         リテラル順序だけが決定する（スイッチ切り替えは本リスト 1 箇所に閉じる）。
        assertTrue(
            "drawerFooterItems の要素は DrawerFooterItem 列挙の部分集合である必要がある",
            footer.all { it in all },
        )
    }
}
