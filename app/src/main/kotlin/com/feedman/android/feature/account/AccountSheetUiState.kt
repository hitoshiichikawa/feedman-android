package com.feedman.android.feature.account

import com.feedman.android.core.model.User

/**
 * アカウントシートの UI 状態（Issue #49 Req 1, 2, 3, 4, 5）。
 *
 * `Hidden` のときシートは描画されない（Req 1.1 の起動操作待ち）。
 * `Visible` のとき、ユーザー領域が以下の 3 つのサブ状態のいずれかで描画される:
 *
 * - [LoadState.Loading]: 現在ユーザー取得が進行中（Req 3.1 / 3.2）
 * - [LoadState.Loaded]:  取得成功（Req 2.1 / 2.2 の email 表示）
 * - [LoadState.Error]:   回復可能エラー（Req 4.1 の再試行ボタン表示）
 *
 * 認証エラー（Req 5.1〜5.3）は本 UI 状態には残さない（[LoadState.Error] に積まずに、
 * one-shot イベントとして UI 側へ流し、シートを閉じてログイン導線へ遷移する）。
 *
 * @property loadState 現在ユーザー領域のロード結果
 */
sealed interface AccountSheetUiState {

    /** 初期状態 / シート未表示。 */
    data object Hidden : AccountSheetUiState

    /**
     * シート表示中。`loadState` でユーザー領域の表示を切替える。
     *
     * @property loadState 現在ユーザー領域のロード結果
     * @property logoutInProgress Issue #50 Req 1.3 / 1.4: ログアウト処理が進行中か。
     *   `true` の間、ログアウトボタンを disabled にし（再押下不可）、ローディング表現を出す。
     *   `false` のときはログアウトボタンを通常表示する。
     * @property deletion Issue #51 Req 1〜5: 退会フロー（二段確認 + 進行中 + 失敗）の状態。
     *   既定は [DeletionState.Idle]（退会フロー未起動）。
     */
    data class Visible(
        val loadState: LoadState,
        val logoutInProgress: Boolean = false,
        val deletion: DeletionState = DeletionState.Idle,
    ) : AccountSheetUiState

    /** ユーザー領域のロード状態（Req 2, 3, 4）。 */
    sealed interface LoadState {

        /** Req 3.1 / 3.2: ローディングインジケータを表示し email を確定値として出さない。 */
        data object Loading : LoadState

        /**
         * Req 2.1 / 2.2: 取得成功。`user.email` を表示し、空 / blank なら代替文言を選ぶ。
         */
        data class Loaded(val user: User) : LoadState

        /**
         * Req 4.1: 回復可能エラー。`message` をユーザー領域に表示し、再試行ボタンで
         * 再フェッチ可能にする。
         *
         * @property message FeedmanException.errorMessage 由来。空のときは UI 側で
         *   汎用文言（state_error / account_sheet_load_error 等）にフォールバックする。
         */
        data class Error(val message: String) : LoadState
    }

    /**
     * Issue #51 退会フローの状態（Req 1〜5）。
     *
     * シート上の「退会」操作からの一連のゲートを表現する:
     *
     * - [Idle]: 退会フロー未起動（既定）。退会ボタンを通常表示する（Req 1.1 / 1.2）
     * - [ConfirmExplanation]: 第 1 段（説明ダイアログ）表示中（Req 1.3 / 2.1 / 2.2）
     * - [ConfirmFinal]: 第 2 段（最終確認ダイアログ）表示中（Req 2.3 / 2.4）
     * - [InProgress]: DELETE /api/users/me 送信中（Req 3.1 / 3.2 / 3.3 / NFR 1.1）
     * - [Error]: 失敗（ローカル状態は維持 / Req 5.1〜5.5）
     *
     * **観測可能挙動**:
     * - `Idle` / `Error` 状態でのみ退会ボタンが通常表示
     * - `ConfirmExplanation` / `ConfirmFinal` / `InProgress` の各状態でログアウト操作を
     *   disabled にする（Req 3.3）
     * - `InProgress` 中はキャンセル操作を受け付けない（Req 3.2）
     */
    sealed interface DeletionState {

        /** 退会フロー未起動。退会ボタンは通常表示。 */
        data object Idle : DeletionState

        /** 第 1 段: 説明ダイアログ（Req 1.3 / 2.1 / 2.2 / 2.5）。 */
        data object ConfirmExplanation : DeletionState

        /** 第 2 段: 最終確認ダイアログ（Req 2.3 / 2.4 / 2.5）。 */
        data object ConfirmFinal : DeletionState

        /** 退会送信中（Req 3.1 / 3.2 / 3.3 / NFR 1.1）。 */
        data object InProgress : DeletionState

        /**
         * 失敗（Req 5.1〜5.5）。`message` を UI に表示し、ユーザーが退会フローを
         * やり直せるよう Idle 経由で再起動可能にする（Req 5.5）。
         *
         * @property message Coordinator が解決した非空文言。
         */
        data class Error(val message: String) : DeletionState
    }
}

/**
 * アカウントシートの one-shot 通知イベント（Issue #49 Req 5.1〜5.3）。
 *
 * `AccountSheetViewModel.events` SharedFlow 経由で UI 側へ流す（replay = 0）。
 */
sealed interface AccountSheetEvent {

    /**
     * Req 5.1 / 5.2 / 5.3: 認証切れ（UNAUTHORIZED）が検知された。
     * UI 側はシートを閉じる必要はなく（ViewModel 側で close 済み）、ログイン導線への
     * 遷移を SessionStateProvider 連動で観測する責務だけを持つ。
     *
     * 本イベントは Req 5.3 に従い「通常エラー表示」と重複させずに発火する。
     */
    data object UnauthorizedRedirect : AccountSheetEvent
}
