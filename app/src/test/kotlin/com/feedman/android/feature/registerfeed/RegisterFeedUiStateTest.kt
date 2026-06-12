package com.feedman.android.feature.registerfeed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RegisterFeedUiState.Visible.canSubmit] の境界値テスト（Issue #44 / Req 1.3 / 1.4 / 3.2）。
 *
 * Composable / Sheet 結線ロジックが活性条件として参照する純粋プロパティを
 * Android 依存なしで検証する。
 */
class RegisterFeedUiStateTest {

    @Test
    fun `Req 1_4 url 空のとき canSubmit は false`() {
        val s = RegisterFeedUiState.Visible(url = "")
        assertFalse(s.canSubmit)
    }

    @Test
    fun `Req 1_4 url が空白のみのとき canSubmit は false`() {
        val s = RegisterFeedUiState.Visible(url = "    ")
        assertFalse(s.canSubmit)
    }

    @Test
    fun `Req 1_3 url が非空のとき canSubmit は true`() {
        val s = RegisterFeedUiState.Visible(url = "https://example.com")
        assertTrue(s.canSubmit)
    }

    @Test
    fun `Req 3_2 submitInProgress true のとき入力があっても canSubmit は false`() {
        val s = RegisterFeedUiState.Visible(
            url = "https://example.com",
            submitInProgress = true,
        )
        assertFalse(s.canSubmit)
    }

    @Test
    fun `url の前後に空白を含むが trim 後に非空なら canSubmit は true`() {
        val s = RegisterFeedUiState.Visible(url = "  https://example.com  ")
        assertTrue(s.canSubmit)
    }
}
