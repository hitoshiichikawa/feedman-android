package com.feedman.android.core.auth

import com.feedman.android.core.model.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SessionStateProvider] の暫定実装（Issue #29 / Req 3.5）。
 *
 * 本 Issue 時点ではまだ実トークン管理（#24 系）が存在しないため、`AppConfig.mockMode` を
 * セッション状態の暫定信号として使う:
 *
 * - `mockMode = true`  → [SessionState.LoggedIn]（ドロワー付きシェルを表示）
 * - `mockMode = false` → [SessionState.LoggedOut]（ログイン画面に差し替え）
 *
 * これは `requirements.md` Requirement 3.5 で許容された「mockMode 連動の暫定信号で表現
 * してよい」に対応する。Issue #24 でトークン保管庫・リフレッシュ結果に基づく実装を導入
 * する際は、Hilt の `@Binds` 1 行差し替えで実装側を入れ替えられるよう、`AppShell` 側は
 * 本 class ではなく [SessionStateProvider] 抽象に依存している（Req 3.5 / NFR 2.2）。
 *
 * 本 Issue では mutable な `MutableStateFlow` を保持しているが、外部公開は
 * [asStateFlow] によって read-only ビューに固定し、UI 側から値を書き換えられないように
 * している。将来 LoggedOut → LoggedIn の遷移（OAuth 完了時）を本実装内で扱う必要が
 * 出た場合に備えた構造であり、本 Issue では起動時に固定された 1 値だけを emit する。
 */
@Singleton
class MockModeSessionStateProvider @Inject constructor(
    appConfig: AppConfig,
) : SessionStateProvider {

    private val _state: MutableStateFlow<SessionState> = MutableStateFlow(
        if (appConfig.mockMode) SessionState.LoggedIn else SessionState.LoggedOut,
    )

    override val state: StateFlow<SessionState> = _state.asStateFlow()
}
