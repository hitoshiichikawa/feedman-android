package com.feedman.android.core.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * セッション状態の最小モデル（Issue #29 / Req 3.5）。
 *
 * 本 Issue では「ログイン未確立 / ログイン確立済み」の二状態だけを表す。トークン管理・
 * リフレッシュ・自動ログアウト等は本格的な実装が走る Issue #24 系で `SessionState` 自体に
 * 詳細を持たせる（Refresh 失敗時の `LoggedOut` への自動遷移など）。
 *
 * 本 Issue ではあくまで「App Shell が描画する画面（ログイン or ドロワー付きシェル）」を
 * 切り替えるための観測対象として扱う（requirements.md Requirement 3）。
 */
sealed class SessionState {

    /**
     * 未認証状態。App Shell はログイン画面に画面全体を差し替える（Req 3.1）。
     */
    data object LoggedOut : SessionState()

    /**
     * 認証済み状態。App Shell はドロワー付きシェル + NavHost を描画する（Req 3.2）。
     */
    data object LoggedIn : SessionState()
}

/**
 * App Shell が観測する [SessionState] のソース（Req 3.5 / NFR 2.2）。
 *
 * 本 Issue では `mockMode` 連動の暫定実装（[com.feedman.android.core.auth.MockModeSessionStateProvider]）
 * だけが Hilt に bind される。後続 Issue（#24 系）でトークン保管庫・リフレッシュ結果・
 * `AuthRepository` から導出する本実装に差し替える前提で、`SessionStateProvider` 抽象を
 * 介して観測対象を差し替え可能にしておく。
 *
 * UI / テスト側は [state] を `StateFlow` として `collectAsStateWithLifecycle` し、テスト時は
 * 任意の `SessionStateProvider` 実装で `LoggedOut` / `LoggedIn` を強制できる（NFR 2.2）。
 */
interface SessionStateProvider {
    val state: StateFlow<SessionState>
}
