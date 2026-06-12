package com.feedman.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolveListFooterState] の状態判定テスト（Issue #28 / Req 1.2, 3.4, 4.1, 4.2 / NFR 3.1）。
 *
 * Req 4.2 「終端フッターは追加ローディング・追加エラーと相互排他で表示される」を
 * 機械的に検証する。同時 true で渡された場合の優先順位（Loading > Error > EndOfList > None）も
 * 排他性の表現として確認する。
 */
class ListFooterStateTest {

    // ─── Req 1.2 / 4.2: 追加読込中は Loading を返し他と排他 ───────────────

    @Test
    fun `Req 1_2 append loading true returns Loading`() {
        // Arrange / Act
        val state = resolveListFooterState(
            isAppendLoading = true,
            isAppendError = false,
            isEndOfPagination = false,
        )
        // Assert
        assertEquals(ListFooterState.Loading, state)
    }

    @Test
    fun `Req 4_2 loading prioritized over error and end of pagination`() {
        // Arrange / Act — 複数 true でも Loading が排他で勝つ
        val state = resolveListFooterState(
            isAppendLoading = true,
            isAppendError = true,
            isEndOfPagination = true,
        )
        // Assert
        assertEquals(ListFooterState.Loading, state)
    }

    // ─── Req 3.4 / 4.2: 追加エラーは Error を返し End / None より優先 ──

    @Test
    fun `Req 3_4 append error true returns Error`() {
        // Arrange / Act
        val state = resolveListFooterState(
            isAppendLoading = false,
            isAppendError = true,
            isEndOfPagination = false,
        )
        // Assert
        assertEquals(ListFooterState.Error, state)
    }

    @Test
    fun `Req 4_2 error prioritized over end of pagination`() {
        // Arrange / Act
        val state = resolveListFooterState(
            isAppendLoading = false,
            isAppendError = true,
            isEndOfPagination = true,
        )
        // Assert
        assertEquals(ListFooterState.Error, state)
    }

    // ─── Req 4.1 / 4.2: 終端は EndOfList を返す ──────────────────────────

    @Test
    fun `Req 4_1 end of pagination true returns EndOfList`() {
        // Arrange / Act
        val state = resolveListFooterState(
            isAppendLoading = false,
            isAppendError = false,
            isEndOfPagination = true,
        )
        // Assert
        assertEquals(ListFooterState.EndOfList, state)
    }

    // ─── 通常状態: None ──────────────────────────────────────────────────

    @Test
    fun `all false returns None`() {
        // Arrange / Act
        val state = resolveListFooterState(
            isAppendLoading = false,
            isAppendError = false,
            isEndOfPagination = false,
        )
        // Assert
        assertEquals(ListFooterState.None, state)
    }
}
