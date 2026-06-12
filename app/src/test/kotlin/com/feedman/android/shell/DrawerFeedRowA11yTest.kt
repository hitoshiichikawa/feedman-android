package com.feedman.android.shell

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DrawerFeedRowA11y] の純粋ロジック単体テスト
 * （Issue #53 Req 1.2 — ドロワー行のまとまった a11y 読み上げ）。
 *
 * 行内の favicon / 状態アイコン / 未読バッジに付与された個別 contentDescription を
 * TalkBack が冗長に列挙するのを避け、行全体を 1 文として読み上げるための
 * string resource ID 解決を検証する。
 *
 * - 状態（active / stopped / error）× 未読件数（あり / なし）の全 6 通りを網羅
 * - 境界値（unread = 0 / 1）を含む
 */
class DrawerFeedRowA11yTest {

    @Test
    fun `active_未読 1 件以上のときは active と未読件数を含むリソースを返す_Req 1_2`() {
        // Arrange
        val statusIcon = FeedStatusIcon.None
        val unread = 5

        // Act
        val resource = DrawerFeedRowA11y.resolve(statusIcon, unread)

        // Assert
        assertEquals(DrawerFeedRowA11yResource.ActiveWithUnread, resource)
        assertEquals(true, resource.hasUnreadArg)
    }

    @Test
    fun `active_未読 0 件のときは未読件数を含まない active リソースを返す_Req 1_2`() {
        // Arrange
        val statusIcon = FeedStatusIcon.None
        val unread = 0

        // Act
        val resource = DrawerFeedRowA11y.resolve(statusIcon, unread)

        // Assert
        assertEquals(DrawerFeedRowA11yResource.ActiveNoUnread, resource)
        assertEquals(false, resource.hasUnreadArg)
    }

    @Test
    fun `stopped_未読 1 件以上のときは停止中と未読件数を含むリソースを返す_Req 1_2`() {
        // Arrange
        val statusIcon = FeedStatusIcon.Stopped
        val unread = 3

        // Act
        val resource = DrawerFeedRowA11y.resolve(statusIcon, unread)

        // Assert
        assertEquals(DrawerFeedRowA11yResource.StoppedWithUnread, resource)
    }

    @Test
    fun `stopped_未読 0 件のときは停止中のみを示すリソースを返す_Req 1_2`() {
        // Arrange / Act / Assert
        assertEquals(
            DrawerFeedRowA11yResource.StoppedNoUnread,
            DrawerFeedRowA11y.resolve(FeedStatusIcon.Stopped, 0),
        )
    }

    @Test
    fun `error_未読 1 件以上のときはエラーと未読件数を含むリソースを返す_Req 1_2`() {
        // Arrange / Act / Assert
        assertEquals(
            DrawerFeedRowA11yResource.ErrorWithUnread,
            DrawerFeedRowA11y.resolve(FeedStatusIcon.Error, 1),
        )
    }

    @Test
    fun `error_未読 0 件のときはエラーのみを示すリソースを返す_Req 1_2`() {
        // Arrange / Act / Assert
        assertEquals(
            DrawerFeedRowA11yResource.ErrorNoUnread,
            DrawerFeedRowA11y.resolve(FeedStatusIcon.Error, 0),
        )
    }

    @Test
    fun `未読が負の値（境界値）の場合も 0 件と同じ扱いになる_Req 1_2`() {
        // Arrange: API 不整合時の防衛的扱い（DrawerFeedRow.shouldShowUnreadBadge と整合）
        // Act
        val resource = DrawerFeedRowA11y.resolve(FeedStatusIcon.None, -1)

        // Assert
        assertEquals(DrawerFeedRowA11yResource.ActiveNoUnread, resource)
    }
}
