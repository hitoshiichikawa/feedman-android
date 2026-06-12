package com.feedman.android.feature.articledetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ArticleDetailContentPolicy] の単体テスト（Issue #36 / Req 2.5, 2.6）。
 *
 * 純粋ロジックのため Android 依存なしで検証する。AC: Req 2.5（本文要素の視認）/ Req 2.6
 * （空のときのプレースホルダ + 続きを読む非表示）。
 */
class ArticleDetailContentPolicyTest {

    // ── resolvePreview ───────────────────────────────────────────────────

    @Test
    fun `resolvePreview は content が非空のとき content を返す`() {
        // Arrange
        val content = "<p>本文</p>"
        val summary = "サマリー"

        // Act
        val result = ArticleDetailContentPolicy.resolvePreview(content = content, summary = summary)

        // Assert (Req 2.5)
        assertEquals(content, result)
    }

    @Test
    fun `resolvePreview は content が空文字列のとき summary に fallback する`() {
        // Arrange
        val summary = "サマリーフォールバック"

        // Act
        val result = ArticleDetailContentPolicy.resolvePreview(content = "", summary = summary)

        // Assert (設計判断 — Open Questions の解決)
        assertEquals(summary, result)
    }

    @Test
    fun `resolvePreview は content が空白のみのとき summary に fallback する`() {
        // Arrange (境界 — 全角・半角スペースのみ)
        val summary = "サマリー"

        // Act
        val result = ArticleDetailContentPolicy.resolvePreview(content = "  \n  ", summary = summary)

        // Assert
        assertEquals(summary, result)
    }

    @Test
    fun `resolvePreview は content が null のとき summary に fallback する`() {
        // Arrange
        val summary = "サマリー"

        // Act (保険 — ItemDetail.content は非 null 前提だが、null も受け付ける)
        val result = ArticleDetailContentPolicy.resolvePreview(content = null, summary = summary)

        // Assert
        assertEquals(summary, result)
    }

    @Test
    fun `resolvePreview は content と summary の両方が空のとき null を返す`() {
        // Act
        val result = ArticleDetailContentPolicy.resolvePreview(content = "", summary = "")

        // Assert (Req 2.6 — 空状態メッセージの起点)
        assertNull(result)
    }

    @Test
    fun `resolvePreview は両方が空白のみのとき null を返す`() {
        // Act
        val result = ArticleDetailContentPolicy.resolvePreview(content = "   ", summary = "\t\n")

        // Assert (境界)
        assertNull(result)
    }

    @Test
    fun `resolvePreview は両方が null のとき null を返す`() {
        // Act
        val result = ArticleDetailContentPolicy.resolvePreview(content = null, summary = null)

        // Assert
        assertNull(result)
    }

    // ── showExpandToggle ──────────────────────────────────────────────────

    @Test
    fun `showExpandToggle は preview が非空のとき true`() {
        // Act
        val result = ArticleDetailContentPolicy.showExpandToggle(preview = "本文あり")

        // Assert (Req 2.2 — 続きを読むボタンを表示)
        assertTrue(result)
    }

    @Test
    fun `showExpandToggle は preview が null のとき false`() {
        // Act
        val result = ArticleDetailContentPolicy.showExpandToggle(preview = null)

        // Assert (Req 2.6 — 続きを読むボタンを非表示)
        assertFalse(result)
    }

    @Test
    fun `showExpandToggle は preview が空白のみのとき false`() {
        // Act (境界)
        val result = ArticleDetailContentPolicy.showExpandToggle(preview = "  ")

        // Assert
        assertFalse(result)
    }
}
