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

    // Favicon サイズバリアント（Issue #26 / Req 4.1, 4.4）
    // — design/mobile/fm-ui.jsx の FMFavicon 利用箇所のサイズに整合させる
    /**
     * Favicon 最小サイズ（16dp）。
     * `FMArticleCard` standard / minimal variant で `size={16}` として使用される行内表示用。
     */
    val faviconExtraSmall: Dp = 16.dp,
    /**
     * Favicon 小サイズ（18dp）。
     * `FMTimelineCard` magazine / cards variant のソース行（`size={18}`）、
     * `FMArticleCard` compact variant（`size={18}`）で使用。
     */
    val faviconSmall: Dp = 18.dp,
    /**
     * Favicon 標準サイズ（28dp）。
     * プロトの `FMFavicon` デフォルト `size = 28`。汎用用途のデフォルト。
     */
    val faviconMedium: Dp = 28.dp,
    /**
     * Favicon 大サイズ（32dp）。
     * `FMTimelineCard` list variant の主アイコン（`size={32}`）、ドロワーのフィードリスト用。
     */
    val faviconLarge: Dp = 32.dp,

    /**
     * Favicon の角丸（Issue #26 / Req 5.1）。プロトの `FMFavicon` は `radius = 8` を既定とし、
     * 小サイズでは `radius={4..5}` を使うが、Compose 実装では Material 3 整合のため
     * 単一の角丸値（8dp）に集約する。
     */
    val faviconCornerRadius: Dp = 8.dp,
)
