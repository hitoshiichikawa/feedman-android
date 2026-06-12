package com.feedman.android.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SPEC §8 が定める寸法トークン（Issue #25 / Req 4）。
 *
 * Compose の [androidx.compose.ui.unit.Dp] 単位で公開し、ピクセル直値は提供しない（Req 4.4）。
 * 角丸は 10dp〜16dp、最小タップ標的 44dp、アイコンは 18dp〜22dp の範囲をカバーする。
 *
 * Material 3 標準の `Shapes` / `Sizes` だけでは SPEC §8 の値を直接表現できないため、
 * テーマ拡張プロパティ（[LocalFeedmanDimens]）として `MaterialTheme` 経由で参照可能にする。
 */
@Immutable
data class FeedmanDimens(
    // 角丸（Req 4.1）— SPEC §8 の 10dp〜16dp 範囲をカバー
    /** カード・チップなど小サイズの角丸（下限値）。 */
    val cornerSmall: Dp = 10.dp,
    /** ボトムシート・大きめカードの角丸（中間値）。 */
    val cornerMedium: Dp = 12.dp,
    /** モーダルシート最上部などの角丸（上限値）。 */
    val cornerLarge: Dp = 16.dp,

    // タップ標的（Req 4.2 / NFR 2.2）— 最小 44dp
    /** Material アクセシビリティ要求も満たす最小タップ標的。 */
    val minTapTarget: Dp = 44.dp,

    // アイコン（Req 4.3）— SPEC §8 の 18dp〜22dp 範囲をカバー
    /** 補助アイコン（行内 favicon 等）。 */
    val iconSmall: Dp = 18.dp,
    /** 標準アイコン（リスト・カード内 lucide-style）。 */
    val iconMedium: Dp = 20.dp,
    /** トップバー等の主要アイコン。 */
    val iconLarge: Dp = 22.dp,
)
