package com.feedman.android.core.ui

import com.feedman.android.core.designsystem.LetterAvatarPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FaviconLogic] の純粋関数群を JVM 単体テストで検証する（Issue #26 / NFR 1.1, 1.3）。
 *
 * 対応 AC:
 * - Req 1.1: data URL 判定（正常系）
 * - Req 2.1: 通常タイトルの頭文字抽出
 * - Req 2.2: data URL でない／復号できない値のフォールバック判定
 * - Req 2.3: 空タイトル時のプレースホルダ `?`
 * - Req 2.4: サロゲートペア・絵文字 1 文字を分割しない
 * - Req 3.1: 同一タイトル → 同一色
 * - Req 3.2: タイトルハッシュから決定論的に選択
 * - Req 3.3: hashCode を用いた安定性（プロセス再生成跨ぎ）
 * - Req 3.4: パレット外の色を返さない
 */
class FaviconLogicTest {

    // ─────────────────────────────────────────────────────────────────────
    //  isDataUrl
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `Req 1_1 isDataUrl returns true for valid data url png`() {
        // Arrange
        val dataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAA"
        // Act
        val result = FaviconLogic.isDataUrl(dataUrl)
        // Assert
        assertTrue(result)
    }

    @Test
    fun `Req 1_1 isDataUrl returns true for data url svg`() {
        assertTrue(FaviconLogic.isDataUrl("data:image/svg+xml;base64,PHN2ZyB4bWxucz0i"))
    }

    @Test
    fun `Req 2_2 isDataUrl returns false for null`() {
        assertFalse(FaviconLogic.isDataUrl(null))
    }

    @Test
    fun `Req 2_2 isDataUrl returns false for empty string`() {
        assertFalse(FaviconLogic.isDataUrl(""))
    }

    @Test
    fun `Req 2_2 isDataUrl returns false for https url`() {
        assertFalse(FaviconLogic.isDataUrl("https://example.com/favicon.ico"))
    }

    @Test
    fun `Req 2_2 isDataUrl returns false for plain text`() {
        assertFalse(FaviconLogic.isDataUrl("not-a-url"))
    }

    @Test
    fun `Req 2_2 isDataUrl returns false for file scheme`() {
        // 境界値: data: ではない別 scheme
        assertFalse(FaviconLogic.isDataUrl("file:///tmp/icon.png"))
    }

    @Test
    fun `Req 1_1 isDataUrl tolerates leading whitespace`() {
        // 堅牢性: API 由来の値に先頭空白が混入しても data URL として扱う
        assertTrue(FaviconLogic.isDataUrl("  data:image/png;base64,abc"))
    }

    // ─────────────────────────────────────────────────────────────────────
    //  extractLetter
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `Req 2_1 extractLetter returns first ascii character`() {
        // Arrange / Act
        val letter = FaviconLogic.extractLetter("Hacker News")
        // Assert
        assertEquals("H", letter)
    }

    @Test
    fun `Req 2_1 extractLetter returns first japanese character`() {
        assertEquals("日", FaviconLogic.extractLetter("日経新聞"))
    }

    @Test
    fun `Req 2_3 extractLetter returns question mark for null`() {
        assertEquals("?", FaviconLogic.extractLetter(null))
    }

    @Test
    fun `Req 2_3 extractLetter returns question mark for empty string`() {
        assertEquals("?", FaviconLogic.extractLetter(""))
    }

    @Test
    fun `Req 2_3 extractLetter returns question mark for whitespace only string`() {
        // 境界値: 空白だけのタイトルもプレースホルダ扱い
        assertEquals("?", FaviconLogic.extractLetter("   "))
    }

    @Test
    fun `Req 2_4 extractLetter keeps emoji as single grapheme`() {
        // Arrange: U+1F4F0 NEWSPAPER は BMP 外（サロゲートペア）
        // Act
        val letter = FaviconLogic.extractLetter("📰 News")
        // Assert: サロゲートペアの片割れではなく完全な 1 コードポイントを返す
        assertEquals("📰", letter)
        assertEquals(2, letter.length) // UTF-16 では 2 char
        assertEquals(1, letter.codePointCount(0, letter.length))
    }

    @Test
    fun `Req 2_4 extractLetter keeps surrogate pair after leading whitespace`() {
        // 境界値: 先頭空白の後にサロゲートペアが来るケース
        val letter = FaviconLogic.extractLetter("  📰News")
        assertEquals("📰", letter)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  pickLetterColor
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `Req 3_1 pickLetterColor returns same color for same title`() {
        // Arrange / Act
        val color1 = FaviconLogic.pickLetterColor("Hacker News")
        val color2 = FaviconLogic.pickLetterColor("Hacker News")
        // Assert
        assertEquals(color1, color2)
    }

    @Test
    fun `Req 3_2 pickLetterColor distributes different titles to different colors`() {
        // Arrange: 視覚的に「色がぶつからない」ことを担保する代表サンプル
        val titles = listOf(
            "Hacker News",
            "日経新聞",
            "GitHub Blog",
            "TechCrunch",
            "Engadget",
            "Verge",
            "Ars Technica",
            "Reuters",
        )
        // Act
        val colors = titles.map { FaviconLogic.pickLetterColor(it) }.toSet()
        // Assert: 8 件以上のタイトルから少なくとも 2 色以上を選ぶ（決定論的分散の最低保証）
        assertTrue(
            "Expected diverse colors across distinct titles but got ${colors.size}",
            colors.size >= 2,
        )
    }

    @Test
    fun `Req 3_3 pickLetterColor uses stable hash across invocations`() {
        // Arrange: String.hashCode は JVM 仕様で安定値が決まっており、
        // 計算式 `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]` を元に決定論的。
        val title = "Hacker News"
        val expectedNormalized = title.hashCode() and Int.MAX_VALUE
        val expectedIndex = expectedNormalized % LetterAvatarPalette.Size
        val expectedColor = LetterAvatarPalette.Colors[expectedIndex]
        // Act
        val actual = FaviconLogic.pickLetterColor(title)
        // Assert
        assertEquals(expectedColor, actual)
    }

    @Test
    fun `Req 3_4 pickLetterColor always returns palette color`() {
        // Arrange: 多様なタイトルを投げてもパレット外が出ないことを確認
        val samples = listOf(
            "A", "B", "C", "あ", "日経", "🦊", "", "  ", "Reuters",
            "long long title with many characters",
            "数字 12345",
        )
        // Act
        val colors = samples.map { FaviconLogic.pickLetterColor(it) }
        // Assert
        colors.forEach { color ->
            assertTrue(
                "Color $color is not in palette",
                LetterAvatarPalette.Colors.contains(color),
            )
        }
    }

    @Test
    fun `Req 3_3 pickLetterColor returns same color for null and empty and blank`() {
        // タイトル欠落系はすべて PLACEHOLDER_LETTER 経由で同一色に集約される
        val nullColor = FaviconLogic.pickLetterColor(null)
        val emptyColor = FaviconLogic.pickLetterColor("")
        val blankColor = FaviconLogic.pickLetterColor("   ")
        assertEquals(nullColor, emptyColor)
        assertEquals(nullColor, blankColor)
    }

    @Test
    fun `Req 3_1 pickLetterColor differs between distinct hashes`() {
        // 境界値: 異なるタイトル同士で、少なくともいくつかは別色になることを確認
        // （パレット 12 色の剰余衝突を許容しつつ、明らかに「全部同じ色」になっていないことを担保）
        val sampleColors = (1..50).map { FaviconLogic.pickLetterColor("Feed-$it") }.toSet()
        assertNotEquals(1, sampleColors.size)
    }
}
