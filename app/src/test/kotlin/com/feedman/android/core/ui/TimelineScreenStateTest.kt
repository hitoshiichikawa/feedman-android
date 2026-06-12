package com.feedman.android.core.ui

import androidx.paging.LoadState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolveTimelineScreenState] の状態判定テスト（Issue #34 / Req 3.1, 3.2, 5.3, 6.1 / NFR 2.1）。
 *
 * Paging 3 の `LazyPagingItems.loadState.refresh` と `itemCount` の組合せから、
 * 画面全体に提示する状態（InitialLoading / InitialError / Empty / Content）が
 * 排他的に決まることを検証する。
 *
 * Pull-to-refresh のエラー時（Req 5.3）は既に表示している一覧が壊れない、つまり
 * `itemCount > 0` のとき refresh=Error でも Content を返すことを境界として確認する。
 */
class TimelineScreenStateTest {

    // ─── Req 3.1: 初回ロード中（記事 0 件 + refresh=Loading）───────────────

    @Test
    fun `Req 3_1 refresh loading and item count zero returns InitialLoading`() {
        // Arrange
        val refresh: LoadState = LoadState.Loading
        // Act
        val state = resolveTimelineScreenState(refresh = refresh, itemCount = 0)
        // Assert
        assertEquals(TimelineScreenState.InitialLoading, state)
    }

    // ─── Req 3.2: 初回ロードエラー（記事 0 件 + refresh=Error）─────────────

    @Test
    fun `Req 3_2 refresh error and item count zero returns InitialError`() {
        // Arrange
        val refresh: LoadState = LoadState.Error(RuntimeException("boom"))
        // Act
        val state = resolveTimelineScreenState(refresh = refresh, itemCount = 0)
        // Assert
        assertEquals(TimelineScreenState.InitialError, state)
    }

    // ─── Req 6.1: 空状態（記事 0 件 + refresh=NotLoading）──────────────────

    @Test
    fun `Req 6_1 refresh not loading and item count zero returns Empty`() {
        // Arrange — endOfPaginationReached=true は終端まで読んで 0 件のケース
        val refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = true)
        // Act
        val state = resolveTimelineScreenState(refresh = refresh, itemCount = 0)
        // Assert
        assertEquals(TimelineScreenState.Empty, state)
    }

    @Test
    fun `Req 6_1 refresh not loading without endOfPagination and item count zero returns Empty`() {
        // Arrange — initial NotLoading (endOfPaginationReached=false) でも 0 件なら Empty
        val refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false)
        // Act
        val state = resolveTimelineScreenState(refresh = refresh, itemCount = 0)
        // Assert
        assertEquals(TimelineScreenState.Empty, state)
    }

    // ─── Content: 通常表示（記事 >= 1 件）──────────────────────────────────

    @Test
    fun `item count positive and refresh not loading returns Content`() {
        // Arrange
        val refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false)
        // Act
        val state = resolveTimelineScreenState(refresh = refresh, itemCount = 5)
        // Assert
        assertEquals(TimelineScreenState.Content, state)
    }

    // ─── Req 5.3: refresh error でも既存一覧があれば Content ──────────────

    @Test
    fun `Req 5_3 refresh error with positive item count keeps Content`() {
        // Arrange — pull-to-refresh が失敗しても表示中の一覧は破壊しない
        val refresh: LoadState = LoadState.Error(RuntimeException("network down"))
        // Act
        val state = resolveTimelineScreenState(refresh = refresh, itemCount = 3)
        // Assert
        assertEquals(TimelineScreenState.Content, state)
    }

    // ─── 境界: refresh=Loading + 既存一覧 → Content（pull-to-refresh 中）──

    @Test
    fun `refresh loading with positive item count returns Content for pull to refresh in progress`() {
        // Arrange — pull-to-refresh 進行中。indicator は別途上位で描画。
        val refresh: LoadState = LoadState.Loading
        // Act
        val state = resolveTimelineScreenState(refresh = refresh, itemCount = 7)
        // Assert
        assertEquals(TimelineScreenState.Content, state)
    }

    // ─── 境界: itemCount=1（最小ポジティブ境界）────────────────────────────

    @Test
    fun `boundary item count one returns Content regardless of refresh state`() {
        // Arrange / Act / Assert — refresh の各 LoadState で itemCount=1 が Content になる
        val notLoading: LoadState = LoadState.NotLoading(endOfPaginationReached = false)
        assertEquals(
            TimelineScreenState.Content,
            resolveTimelineScreenState(refresh = notLoading, itemCount = 1),
        )
        assertEquals(
            TimelineScreenState.Content,
            resolveTimelineScreenState(refresh = LoadState.Loading, itemCount = 1),
        )
        assertEquals(
            TimelineScreenState.Content,
            resolveTimelineScreenState(
                refresh = LoadState.Error(IllegalStateException("x")),
                itemCount = 1,
            ),
        )
    }
}
