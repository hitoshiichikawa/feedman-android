package com.feedman.android.core.auth

import com.feedman.android.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SessionStateProvider] の AuthRepository 連動実装（Issue #23 Req 3.3 / Req 1.2）。
 *
 * `AuthRepository.observeIsAuthenticated()` を購読し、`Boolean` を [SessionState] にマップして
 * AppShell に流す:
 *
 * - `true`  → [SessionState.LoggedIn]
 * - `false` → [SessionState.LoggedOut]
 *
 * これにより、`LoginViewModel` が `AuthRepository.exchange` を呼んで成功した直後（Req 3.3）
 * に AppShell が自動的に LoggedIn に切替わる。`mockMode = true` のときは
 * [MockModeSessionStateProvider] が代わりにバインドされるため、本実装は **mockMode = false の
 * 実環境向け** に限定される（DI 切替は [com.feedman.android.di.AuthModule]）。
 *
 * ## 起動時の初期状態（Issue #24 で本格化）
 *
 * 本 Issue では「ログイン直後の遷移が成立する範囲」までを保証する。起動時に保存済みトークン
 * から復元する処理は #24 で扱うため、起動直後は `observeIsAuthenticated()` の初期値
 * （`false`）に従って LoggedOut から始まる。Issue #24 で起動時 refresh を導入したタイミングで
 * `false → true` 遷移が `currentlyDark` などと同様に AppShell に反映される。
 */
@Singleton
class AuthRepositorySessionStateProvider @Inject constructor(
    authRepository: AuthRepository,
    @ApplicationScope scope: CoroutineScope,
) : SessionStateProvider {

    override val state: StateFlow<SessionState> = authRepository
        .observeIsAuthenticated()
        .map { isAuthenticated ->
            if (isAuthenticated) SessionState.LoggedIn else SessionState.LoggedOut
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = if (authRepository.observeIsAuthenticated().value) {
                SessionState.LoggedIn
            } else {
                SessionState.LoggedOut
            },
        )
}
