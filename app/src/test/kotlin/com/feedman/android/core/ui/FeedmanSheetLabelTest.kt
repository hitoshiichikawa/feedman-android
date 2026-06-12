package com.feedman.android.core.ui

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FeedmanSheetLabel] の境界テスト（Issue #28 / Req 6.4 / NFR 3.1）。
 *
 * FeedmanSheet 自体は Compose 描画なので instrumented 領分（NFR 3.2）だが、
 * Req 6.4 の「スクリーンリーダーから参照可能なラベル」を保証する境界判定（空白拒否）は
 * 純粋関数として分離し、JVM 単体テストでカバーする。
 */
class FeedmanSheetLabelTest {

    // ─── Req 6.4: ラベルは非空である必要 ───────────────────────────────

    @Test
    fun `Req 6_4 empty label is rejected`() {
        // Arrange / Act / Assert
        val ex = assertThrows(IllegalArgumentException::class.java) {
            FeedmanSheetLabel.validate("")
        }
        assertTrue(
            "Error message should reference Req 6.4",
            ex.message!!.contains("Req 6.4"),
        )
    }

    @Test
    fun `Req 6_4 whitespace only label is rejected`() {
        // Arrange / Act / Assert
        assertThrows(IllegalArgumentException::class.java) {
            FeedmanSheetLabel.validate("   ")
        }
    }

    @Test
    fun `Req 6_4 valid label passes`() {
        // Arrange / Act — 例外が出なければ pass
        FeedmanSheetLabel.validate("記事の詳細")
        // Assert: 到達できれば pass
    }
}
