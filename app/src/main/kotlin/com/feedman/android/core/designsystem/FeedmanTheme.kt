package com.feedman.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Feedman 独自トークン（Req 2.3, 2.4）。
 *
 * Material 3 標準の [ColorScheme] スロットに収まりきらない、プロト
 * `design/mobile/fm-data.jsx` の `FM_THEME` 由来のトークン群を保持する。
 *
 * @property cardBackground カード地（light: surface, dark: surface）の正本。
 *           Material 3 では `surface` と `surfaceContainer` のいずれを使うか迷いやすいため、
 *           本プロジェクト独自のセマンティクスとして固定する。
 * @property readForegroundAlpha 既読項目向け前景の opacity 倍率（SPEC §5.1, fm-data.jsx の
 *           `opacity 0.55`）。fg / mutedFg に乗算して既読の見た目を作る。
 * @property star スター色（FM_THEME.star を独立公開、M3 ロールに丸めない）。
 * @property danger 破壊的操作色（FM_THEME.danger を独立公開、M3 `error` ではなく独自ロール）。
 * @property accentSoft アクセントの薄い背景色（チップ・バッジ等）。
 * @property border 通常の境界線色。
 * @property borderStrong 強調境界線色。
 * @property mutedFg muted な前景（補助テキスト）。
 * @property scrim モーダル背後の暗幕。
 */
@Immutable
data class FeedmanExtendedColors(
    val cardBackground: Color,
    val readForegroundAlpha: Float,
    val star: Color,
    val danger: Color,
    val accentSoft: Color,
    val border: Color,
    val borderStrong: Color,
    val mutedFg: Color,
    val scrim: Color,
)

/** 既読項目の前景 opacity（SPEC §5.1, fm-data.jsx の opacity 0.55）。 */
internal const val READ_FOREGROUND_ALPHA: Float = 0.55f

internal val LightFeedmanExtendedColors = FeedmanExtendedColors(
    cardBackground = FeedmanColors.LightSurface,
    readForegroundAlpha = READ_FOREGROUND_ALPHA,
    star = FeedmanColors.LightStar,
    danger = FeedmanColors.LightDanger,
    accentSoft = FeedmanColors.LightAccentSoft,
    border = FeedmanColors.LightBorder,
    borderStrong = FeedmanColors.LightBorderStrong,
    mutedFg = FeedmanColors.LightMutedFg,
    scrim = FeedmanColors.LightScrim,
)

internal val DarkFeedmanExtendedColors = FeedmanExtendedColors(
    cardBackground = FeedmanColors.DarkSurface,
    readForegroundAlpha = READ_FOREGROUND_ALPHA,
    star = FeedmanColors.DarkStar,
    danger = FeedmanColors.DarkDanger,
    accentSoft = FeedmanColors.DarkAccentSoft,
    border = FeedmanColors.DarkBorder,
    borderStrong = FeedmanColors.DarkBorderStrong,
    mutedFg = FeedmanColors.DarkMutedFg,
    scrim = FeedmanColors.DarkScrim,
)

/**
 * FM_THEME（light）を Material 3 [ColorScheme] スロットへマッピング（Req 2.1）。
 *
 * - primary       = accent (Indigo light)
 * - onPrimary     = accentOn (#FFF)
 * - background    = bg (#FAFAFA)
 * - surface       = surface (#FFFFFF)
 * - surfaceVariant= muted (#F5F5F5)
 * - onSurfaceVariant = mutedFg (#737373)
 * - outline       = border (#E5E5E5)
 * - outlineVariant= borderStrong は強調用なので outlineVariant に合わせず独自で保持
 * - error         = danger は独自トークン化（Req 2.4）するため、M3 error にもダミー的に設定する
 */
internal val FeedmanLightColorScheme: ColorScheme = lightColorScheme(
    primary = FeedmanColors.LightAccent,
    onPrimary = FeedmanColors.LightAccentOn,
    primaryContainer = FeedmanColors.LightAccentSoft,
    onPrimaryContainer = FeedmanColors.LightFg,
    secondary = FeedmanColors.LightAccent,
    onSecondary = FeedmanColors.LightAccentOn,
    background = FeedmanColors.LightBg,
    onBackground = FeedmanColors.LightFg,
    surface = FeedmanColors.LightSurface,
    onSurface = FeedmanColors.LightFg,
    surfaceVariant = FeedmanColors.LightMuted,
    onSurfaceVariant = FeedmanColors.LightMutedFg,
    surfaceTint = FeedmanColors.LightAccent,
    outline = FeedmanColors.LightBorder,
    outlineVariant = FeedmanColors.LightBorderStrong,
    error = FeedmanColors.LightDanger,
    onError = FeedmanColors.LightAccentOn,
    scrim = FeedmanColors.LightScrim,
)

/** FM_THEME(dark) → Material 3 [ColorScheme] マッピング（Req 2.1）。 */
internal val FeedmanDarkColorScheme: ColorScheme = darkColorScheme(
    primary = FeedmanColors.DarkAccent,
    onPrimary = FeedmanColors.DarkAccentOn,
    primaryContainer = FeedmanColors.DarkAccentSoft,
    onPrimaryContainer = FeedmanColors.DarkFg,
    secondary = FeedmanColors.DarkAccent,
    onSecondary = FeedmanColors.DarkAccentOn,
    background = FeedmanColors.DarkBg,
    onBackground = FeedmanColors.DarkFg,
    surface = FeedmanColors.DarkSurface,
    onSurface = FeedmanColors.DarkFg,
    surfaceVariant = FeedmanColors.DarkMuted,
    onSurfaceVariant = FeedmanColors.DarkMutedFg,
    surfaceTint = FeedmanColors.DarkAccent,
    outline = FeedmanColors.DarkBorder,
    outlineVariant = FeedmanColors.DarkBorderStrong,
    error = FeedmanColors.DarkDanger,
    onError = FeedmanColors.DarkAccentOn,
    scrim = FeedmanColors.DarkScrim,
)

/**
 * Feedman 拡張トークンに `MaterialTheme.feedmanColors` でアクセスするための CompositionLocal。
 *
 * [compositionLocalOf] を用いることで、テーマモード切替時に再コンポジションが起き
 * 配下の Composable へ追従する（Req 2.2 / Req 3.3）。
 */
val LocalFeedmanExtendedColors = compositionLocalOf<FeedmanExtendedColors> {
    error("FeedmanExtendedColors not provided. Wrap your composables with FeedmanTheme { ... }.")
}

/**
 * 寸法トークンの CompositionLocal。寸法は端末再構成で値が変わらないため
 * [staticCompositionLocalOf] でコスト削減する。
 */
val LocalFeedmanDimens = staticCompositionLocalOf { FeedmanDimens() }

/**
 * Material 3 `MaterialTheme` を拡張した Feedman 用テーマアクセサ。
 * `MaterialTheme.feedmanColors` / `MaterialTheme.feedmanDimens` で参照する。
 */
object FeedmanTokens {
    val colors: FeedmanExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFeedmanExtendedColors.current

    val dimens: FeedmanDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalFeedmanDimens.current
}

/** Feedman 拡張トークンへのアクセサ（`MaterialTheme.feedmanColors` 形式）。 */
val MaterialTheme.feedmanColors: FeedmanExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedmanExtendedColors.current

/** 寸法トークンへのアクセサ（`MaterialTheme.feedmanDimens` 形式）。 */
val MaterialTheme.feedmanDimens: FeedmanDimens
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedmanDimens.current

/**
 * アプリ全体に適用する Feedman テーマ（Issue #25 / Req 2.1, 2.2, 3.3）。
 *
 * @param useDarkTheme `true` のときダーク用 [ColorScheme] と拡張トークンを適用する。
 *        既定は端末のダークモード設定追従（[isSystemInDarkTheme]）。テーマモード
 *        オーバーライドを反映するための解決は呼び出し側（例: `MainActivity`）で行う。
 * @param content 配下に描画する Composable。
 */
@Composable
fun FeedmanTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) FeedmanDarkColorScheme else FeedmanLightColorScheme
    val extended = if (useDarkTheme) DarkFeedmanExtendedColors else LightFeedmanExtendedColors
    androidx.compose.runtime.CompositionLocalProvider(
        LocalFeedmanExtendedColors provides extended,
        LocalFeedmanDimens provides FeedmanDimens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
