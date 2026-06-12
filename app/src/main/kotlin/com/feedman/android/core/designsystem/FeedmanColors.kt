package com.feedman.android.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Feedman プロトタイプ `design/mobile/fm-data.jsx` の `FM_THEME` を正本とした
 * ARGB 配色定数（Issue #25 / Req 1）。
 *
 * 各定数のコメントには換算元の oklch 値（CSS Color Module Level 4 形式）を保持し、
 * `FM_THEME` の更新時に目視で差分を照合できる状態を保つ（NFR 1.2）。
 *
 * ## oklch → sRGB 換算の方法
 *
 * 1. oklch(L C h) → OKLab: a = C·cos(h), b = C·sin(h)
 * 2. OKLab → linear sRGB（CSS Color Module Level 4 §16.7 の行列）
 * 3. linear sRGB → sRGB（gamma compression, srgb companding）
 * 4. 各チャンネルを [0, 255] にクランプして 8bit 化
 *
 * 換算はビルド時計算ではなく事前計算した定数として埋め込み、`FM_THEME` の oklch 値
 * との対応を本ファイル内のコメントで担保する（Req 1.3）。換算スクリプトと
 * 期待値は `docs/specs/25-feedman-theme-tokens-for-material-3/impl-notes.md` を参照。
 *
 * ## 採用アクセント（Req 1.2）
 *
 * アクセントは Indigo（light: oklch(0.55 0.17 264) / dark: oklch(0.68 0.15 264)）のみ。
 * fm-data.jsx の FM_ACCENTS には coral / teal / violet も含まれるが、SPEC §8 で
 * Indigo に確定済みのため、本モジュールでは Indigo 系統のみを公開する。
 */
object FeedmanColors {

    // ─────────────────────────────────────────────────────────────────────
    //  Light palette — derived from FM_THEME(dark=false, accentKey='indigo')
    //  fm-data.jsx L28-L34
    // ─────────────────────────────────────────────────────────────────────

    /** oklch(0.985 0 0) → #FAFAFA — page-level 背景 */
    val LightBg: Color = Color(0xFFFAFAFA)

    /** oklch(1 0 0) → #FFFFFF — 主サーフェス（カード地など） */
    val LightSurface: Color = Color(0xFFFFFFFF)

    /** oklch(0.975 0 0) → #F7F7F7 — 2 段目サーフェス（入れ子カード等） */
    val LightSurface2: Color = Color(0xFFF7F7F7)

    /** oklch(0.205 0 0) → #171717 — 本文 / 主前景 */
    val LightFg: Color = Color(0xFF171717)

    /** oklch(0.97 0 0) → #F5F5F5 — muted 背景（フィルタチップ等） */
    val LightMuted: Color = Color(0xFFF5F5F5)

    /** oklch(0.556 0 0) → #737373 — muted 前景（補助テキスト） */
    val LightMutedFg: Color = Color(0xFF737373)

    /** oklch(0.922 0 0) → #E5E5E5 — 通常の境界線 */
    val LightBorder: Color = Color(0xFFE5E5E5)

    /** oklch(0.87 0 0) → #D4D4D4 — 強調境界線 */
    val LightBorderStrong: Color = Color(0xFFD4D4D4)

    /** oklch(0.78 0.16 84) → #E7AD01 — スター色 */
    val LightStar: Color = Color(0xFFE7AD01)

    /** oklch(0.577 0.245 27) → #E7000F — 破壊的操作色 */
    val LightDanger: Color = Color(0xFFE7000F)

    /** rgba(0, 0, 0, 0.32) → 0x52000000 — スクリム（モーダル背後の暗幕） */
    val LightScrim: Color = Color(0x52000000)

    /** oklch(0.55 0.17 264) → #3C6AD3 — アクセント（Indigo, light） */
    val LightAccent: Color = Color(0xFF3C6AD3)

    /** fm-data.jsx FM_ACCENTS.indigo.on（'#ffffff'）— accent 上の前景色 */
    val LightAccentOn: Color = Color(0xFFFFFFFF)

    /**
     * color-mix(in oklch, accent 12%, white) — OKLab 線形補間で事前計算。
     * accent oklch(0.55 0.17 264) を 12% / white oklch(1 0 0) を 88% で混色 → #E6EDFB
     */
    val LightAccentSoft: Color = Color(0xFFE6EDFB)

    // ─────────────────────────────────────────────────────────────────────
    //  Dark palette — derived from FM_THEME(dark=true, accentKey='indigo')
    //  fm-data.jsx L18-L25
    // ─────────────────────────────────────────────────────────────────────

    /** oklch(0.145 0 0) → #0A0A0A */
    val DarkBg: Color = Color(0xFF0A0A0A)

    /** oklch(0.205 0 0) → #171717 */
    val DarkSurface: Color = Color(0xFF171717)

    /** oklch(0.235 0 0) → #1E1E1E */
    val DarkSurface2: Color = Color(0xFF1E1E1E)

    /** oklch(0.985 0 0) → #FAFAFA */
    val DarkFg: Color = Color(0xFFFAFAFA)

    /** oklch(0.269 0 0) → #262626 */
    val DarkMuted: Color = Color(0xFF262626)

    /** oklch(0.708 0 0) → #A1A1A1 */
    val DarkMutedFg: Color = Color(0xFFA1A1A1)

    /** oklch(1 0 0 / 12%) → 0x1FFFFFFF（白を 12% alpha） */
    val DarkBorder: Color = Color(0x1FFFFFFF)

    /** oklch(1 0 0 / 20%) → 0x33FFFFFF（白を 20% alpha） */
    val DarkBorderStrong: Color = Color(0x33FFFFFF)

    /** oklch(0.82 0.16 84) → #F5BA26 */
    val DarkStar: Color = Color(0xFFF5BA26)

    /** oklch(0.704 0.191 22) → #FF6468 */
    val DarkDanger: Color = Color(0xFFFF6468)

    /** rgba(0, 0, 0, 0.6) → 0x99000000 */
    val DarkScrim: Color = Color(0x99000000)

    /** oklch(0.68 0.15 264) → #6895F4 — アクセント（Indigo, dark） */
    val DarkAccent: Color = Color(0xFF6895F4)

    /** fm-data.jsx FM_ACCENTS.indigo.on（'#ffffff'）— accent 上の前景色（dark でも同値） */
    val DarkAccentOn: Color = Color(0xFFFFFFFF)

    /**
     * color-mix(in oklch, accent 18%, transparent) — accent を 18% alpha で乗せる。
     * accent oklch(0.68 0.15 264) を不透明 #6895F4 として、alpha = round(0.18 * 255) = 0x2E
     */
    val DarkAccentSoft: Color = Color(0x2E6895F4)
}
