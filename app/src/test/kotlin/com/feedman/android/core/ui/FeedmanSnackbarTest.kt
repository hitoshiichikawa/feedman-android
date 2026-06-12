package com.feedman.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FeedmanSnackbar] の検証ロジックテスト（Issue #28 / Req 5.4, 5.5 / NFR 3.1）。
 *
 * Compose 描画側（[SnackbarHostState] とのインタラクション）は instrumented テスト領分
 * （NFR 3.2）なので、本 JVM 単体テストでは「ヘルパ内に固定文言を持たない」境界
 * （空文字列拒否）の検証ロジックのみをカバーする。
 */
class FeedmanSnackbarTest {

    // ─── Req 5.4: ヘルパ内に固定文言を持たない → 空メッセージは拒否 ──

    @Test
    fun `Req 5_4 empty message is rejected`() {
        // Arrange / Act / Assert
        val ex = assertThrows(IllegalArgumentException::class.java) {
            FeedmanSnackbar.validateMessage("")
        }
        assertTrue(
            "Error message should mention Req 5.4 reason",
            ex.message!!.contains("blank"),
        )
    }

    @Test
    fun `Req 5_4 whitespace only message is rejected`() {
        // Arrange / Act / Assert — 全角・半角の空白のみは "fallback" を要求しないと
        // 何も表示できないので拒否（Req 5.4: ヘルパ内に固定文言を持たないため）
        assertThrows(IllegalArgumentException::class.java) {
            FeedmanSnackbar.validateMessage("   ")
        }
    }

    @Test
    fun `non empty message passes validation`() {
        // Arrange / Act — 例外が出なければ pass
        FeedmanSnackbar.validateMessage("保存しました")
        // Assert: 到達できれば pass（明示）
        assertEquals(Unit, Unit)
    }

    // ─── Req 5.5: アクションラベル境界 ──────────────────────────────────

    @Test
    fun `Req 5_5 empty action label is rejected`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) {
            FeedmanSnackbar.validateActionLabel("")
        }
    }

    @Test
    fun `Req 5_5 valid action label passes`() {
        // Arrange / Act
        FeedmanSnackbar.validateActionLabel("元に戻す")
        // Assert
        assertEquals(Unit, Unit)
    }
}
