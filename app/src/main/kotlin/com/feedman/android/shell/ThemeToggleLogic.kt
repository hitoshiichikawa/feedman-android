package com.feedman.android.shell

import com.feedman.android.core.designsystem.ThemeMode

/**
 * トップバーのテーマ切替アイコン押下時に使う、現在モードから次モードへの遷移ロジック
 * （Issue #31 / Req 3.3）。
 *
 * プロトタイプ `design/mobile/fm-screens.jsx` の `actions.toggleTheme` がライト ↔ ダークの
 * 2 値トグルである（FMHeader 内で `T.statusDark ? 'sun' : 'moon'` を切り替える）。本 Issue は
 * 「ダーク現在 → ライトへ」「ライト現在 → ダークへ」の 2 ケースに加え、ThemeMode が
 * FOLLOW_SYSTEM の場合は **直前に画面が表示していたモードと逆**を採用する:
 *
 * - 現在 [ThemeMode.LIGHT] → [ThemeMode.DARK]
 * - 現在 [ThemeMode.DARK] → [ThemeMode.LIGHT]
 * - 現在 [ThemeMode.FOLLOW_SYSTEM] かつ `currentlyDark = true` → [ThemeMode.LIGHT]
 * - 現在 [ThemeMode.FOLLOW_SYSTEM] かつ `currentlyDark = false` → [ThemeMode.DARK]
 *
 * 純粋関数として実装し、Composable 起動なしで JVM 単体テスト対象とする。
 *
 * @param currentMode 現在保存されている [ThemeMode]
 * @param currentlyDark 現在の表示が暗色か。FOLLOW_SYSTEM のときの判定に使用。
 *   LIGHT / DARK 固定時はこの値を参照しない（モード値で一意に決まるため）
 *
 * @return 切替後の [ThemeMode]
 */
fun nextThemeMode(currentMode: ThemeMode, currentlyDark: Boolean): ThemeMode = when (currentMode) {
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.LIGHT
    ThemeMode.FOLLOW_SYSTEM -> if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
}

/**
 * トップバーに表示すべきテーマ切替アイコン種別（Issue #31 / Req 3.1, 3.2）。
 *
 * プロト準拠で `T.statusDark` が真なら sun（ライトへ切替）/ 偽なら moon（ダークへ切替）。
 */
enum class ThemeToggleIcon {
    /** 現在ダーク表示 → タップでライトに切り替え（プロト: sun アイコン）。 */
    SunIndicatingSwitchToLight,

    /** 現在ライト表示 → タップでダークに切り替え（プロト: moon アイコン）。 */
    MoonIndicatingSwitchToDark,
}

/**
 * 現在の表示が暗色かどうかから、表示するアイコンを決定する純粋関数（Req 3.1, 3.2）。
 *
 * @param currentlyDark 現在画面が暗色表示中なら `true`。
 */
fun resolveThemeToggleIcon(currentlyDark: Boolean): ThemeToggleIcon =
    if (currentlyDark) ThemeToggleIcon.SunIndicatingSwitchToLight
    else ThemeToggleIcon.MoonIndicatingSwitchToDark
