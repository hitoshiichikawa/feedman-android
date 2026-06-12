package com.feedman.android.core.designsystem

/**
 * ユーザーが選択可能なテーマモード（Issue #25 / Req 3.1）。
 *
 * - [FOLLOW_SYSTEM] — 端末のダークモード設定に追従（既定値, Req 3.2）。
 * - [LIGHT] — ライト固定。
 * - [DARK] — ダーク固定。
 *
 * 永続化と読み込みは [ThemeModeRepository] が扱う。
 */
enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** Req 3.2 / 3.6: 既定値（フォールバックを含む）。 */
        val DEFAULT: ThemeMode = FOLLOW_SYSTEM
    }
}
