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
     */
    data class Visible(
        val loadState: LoadState,
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
