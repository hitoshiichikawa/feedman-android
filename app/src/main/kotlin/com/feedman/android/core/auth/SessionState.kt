package com.feedman.android.core.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * App Shell が観測するセッション状態（Issue #24 / requirements.md）。
 *
 * 起動直後の「保存済みトークンを使った復元中」をまず [Restoring] として表現し、復元結果
 * （成功 / トークン無し / 認証切れ / ネットワーク失敗）に応じて [LoggedIn] または
 * [LoggedOut] に確定する。確定後は AuthRepository.observeIsAuthenticated() の変化に
 * 追従して以降の遷移を行う:
 *
 * - [Restoring]: アプリ起動時の初期状態。App Shell はスプラッシュ相当のローディング表示を
 *   描画する（Req 1.1 / Req 2.1）。
 * - [LoggedIn]: 認証済み。App Shell はドロワー付きシェルを描画する（Req 3.1）。
 * - [LoggedOut]: 未認証。App Shell はログイン画面を描画する（Req 4.1）。
 *
 * Issue #21〜#23 で導入された 2 状態（LoggedOut / LoggedIn）から拡張する形で、Restoring を
 * 追加することで「起動時に一瞬ログイン画面がちらつく」問題を解消する（requirements.md
 * Introduction）。
 */
sealed class SessionState {

    /**
     * 起動時の復元処理が進行中の状態。App Shell はスプラッシュ相当のローディング表示を
     * 描画する（Req 1.1 / Req 2.1 / 2.2 / 2.3）。
     */
    data object Restoring : SessionState()

    /**
     * 未認証状態。App Shell はログイン画面に画面全体を差し替える（Req 4.1 / 4.2）。
     */
    data object LoggedOut : SessionState()

    /**
     * 認証済み状態。App Shell はドロワー付きシェル + NavHost を描画する（Req 3.1 / 3.2）。
     */
    data object LoggedIn : SessionState()
}

/**
 * App Shell が観測する [SessionState] のソース（Req 5.2 / NFR 2.1 / NFR 2.2）。
 *
 * 同一プロセス内で観測される現在のセッション状態が常に一意になるよう、Hilt で
 * Singleton としてバインドされる。`AppConfig.mockMode` に応じて
 * [com.feedman.android.core.auth.AuthRepositorySessionStateProvider] または
 * [com.feedman.android.core.auth.MockModeSessionStateProvider] が DI で供給される
 * （[com.feedman.android.di.AuthModule]）。
 *
 * UI / テスト側は [state] を `StateFlow` として `collectAsStateWithLifecycle` し、テスト時は
 * 任意の `SessionStateProvider` 実装で `Restoring` / `LoggedOut` / `LoggedIn` を強制できる。
 */
interface SessionStateProvider {
    val state: StateFlow<SessionState>
}
