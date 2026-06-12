package com.feedman.android.core.ui

import androidx.compose.ui.graphics.Color
import com.feedman.android.core.designsystem.LetterAvatarPalette

/**
 * Favicon Composable のロジック層（Issue #26 / NFR 1.1, 1.2）。
 *
 * UI 描画から分離し、JVM 単体テストから検証可能な純粋関数群として提供する。
 * 同一入力に対して常に同一の出力を返す（NFR 1.2）ため、アプリ再起動・プロセス再生成を
 * またいでも同一フィードタイトルから同一色が選ばれる（Req 3.3）。
 */
object FaviconLogic {

    /**
     * favicon 文字列が data URL（`data:<mime>;base64,...`）として扱えるかを判定する（Req 1.1, 2.2）。
     *
     * 判定条件: `null` でなく、`data:` プレフィックスで始まる（先頭スペースを除去して比較）。
     * 実際に base64 として復号可能かどうかは Coil の onError コールバックで判定し、
     * 復号失敗時もフォールバックする（Req 2.2）。
     *
     * @param faviconValue API レスポンスの `favicon_url` / `feed_favicon_url` 相当の文字列
     * @return `data:` で始まる空でない文字列なら `true`、それ以外（`null` / 空 / 別スキーム）は `false`
     */
    fun isDataUrl(faviconValue: String?): Boolean {
        if (faviconValue.isNullOrEmpty()) return false
        // 前後空白を許容して比較（API 由来の値は trim される想定だがロバストに）
        return faviconValue.trimStart().startsWith("data:")
    }

    /**
     * フィードタイトルからレターアバター先頭 1 文字を抽出する（Req 2.1, 2.3, 2.4）。
     *
     * - `null` / 空文字 / 空白のみ → プレースホルダ `?`（Req 2.3）
     * - Unicode コードポイント 1 つ分を抽出し、サロゲートペアや絵文字 1 文字を分割しない（Req 2.4）
     *
     * @param feedTitle フィードタイトル
     * @return レターアバターの先頭 1 文字
     */
    fun extractLetter(feedTitle: String?): String {
        if (feedTitle.isNullOrBlank()) return PLACEHOLDER_LETTER
        // 先頭の空白文字（Unicode 仕様の空白を含む）をスキップしてから 1 コードポイント取得
        val trimmed = feedTitle.trimStart()
        if (trimmed.isEmpty()) return PLACEHOLDER_LETTER
        val codePoint = trimmed.codePointAt(0)
        return String(Character.toChars(codePoint))
    }

    /**
     * フィードタイトルから決定論的に背景色を選ぶ（Req 3.1, 3.2, 3.3, 3.4）。
     *
     * - 同一タイトル → 同一色（Req 3.1）
     * - タイトル文字列のハッシュから [LetterAvatarPalette] のインデックスを決定する（Req 3.2）
     * - JVM 標準の [String.hashCode] は仕様で定義されており、プロセス再生成をまたいでも安定する（Req 3.3）
     * - パレット外の色は返さない（Req 3.4）
     *
     * `null` / 空文字 / 空白のみのタイトルでは [PLACEHOLDER_LETTER] と同じ正規化キーを用いる。
     * これによりタイトル欠落フィードは同じ色（パレット先頭の slate）に集約される。
     *
     * @param feedTitle フィードタイトル
     * @return [LetterAvatarPalette.Colors] から選ばれた背景色
     */
    fun pickLetterColor(feedTitle: String?): Color {
        val key = if (feedTitle.isNullOrBlank()) PLACEHOLDER_LETTER else feedTitle
        // hashCode は Int.MIN_VALUE を含む全 Int を返しうるため、絶対値で 0..Int.MAX_VALUE に
        // 正規化する。Math.abs(Int.MIN_VALUE) は Int.MIN_VALUE を返す既知の罠があるため、
        // bit mask で正の領域に落とす。
        val normalized = key.hashCode() and Int.MAX_VALUE
        val index = normalized % LetterAvatarPalette.Size
        return LetterAvatarPalette.Colors[index]
    }

    /** タイトル欠落時のプレースホルダ文字（Req 2.3）。 */
    const val PLACEHOLDER_LETTER: String = "?"
}
