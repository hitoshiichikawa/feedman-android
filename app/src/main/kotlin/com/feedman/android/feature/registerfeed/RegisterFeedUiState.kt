package com.feedman.android.feature.registerfeed

/**
 * フィード登録シートの UI 状態（Issue #44 / requirements.md Req 1, 2, 3, 5）。
 *
 * シングルトン状態（[Hidden] / [Visible]）として保持し、Hidden 時はシートを描画しない
 * （Req 1.5）。Visible 時は URL 入力欄・送信ボタン・エラー表示を持つ。
 *
 * @property url URL 入力欄の現在値（trim 前の生入力）。Req 1.3 で入力値を保持する
 * @property submitInProgress 送信中フラグ。`true` のとき送信ボタンをローディング表示にし
 *   入力欄編集と再送信を抑止する（Req 3.2, 3.3）
 * @property clientErrorMessage クライアント側 URL バリデーション失敗時のエラー文言（Req 2.1）。
 *   null の場合は表示しない。サーバー由来エラーとは独立した管理（Req 2.3, 5.8 で文言クリア対象）
 * @property serverErrorMessage サーバー応答に起因するエラー文言（Req 5.1〜5.6）。
 *   null の場合は表示しない。Req 5.8 で入力変更時にクリアする
 */
sealed interface RegisterFeedUiState {

    /** 初期状態 / シート未表示。 */
    data object Hidden : RegisterFeedUiState

    /** シート表示中。 */
    data class Visible(
        val url: String = "",
        val submitInProgress: Boolean = false,
        val clientErrorMessage: String? = null,
        val serverErrorMessage: String? = null,
    ) : RegisterFeedUiState {

        /**
         * 送信ボタンの活性条件（Req 1.3 / 1.4 / 3.2）。
         *
         * - 入力欄が空または空白のみは無効（Req 1.4: 送信抑止）
         * - 送信進行中は無効（Req 3.2: 二重送信抑止）
         */
        val canSubmit: Boolean
            get() = !submitInProgress && url.trim().isNotEmpty()
    }
}

/**
 * フィード登録シートの one-shot 通知イベント（Issue #44 / Issue #45）。
 *
 * `RegisterFeedViewModel.events` SharedFlow 経由で UI 側へ流す（replay = 0）。
 */
sealed interface RegisterFeedEvent {

    /**
     * Req 4.1 / 4.2: 登録成功（UI 側でシートを閉じ、トーストを表示する）。
     */
    data object RegistrationSucceeded : RegisterFeedEvent

    /**
     * Issue #45 Req 3.1 / 3.2 / 3.3: 登録自体は成功したが、その直後に走らせた
     * 購読一覧再取得（Subscription Repository の `refresh()` 経由）が失敗したことを
     * AppShell へ通知する one-shot イベント。
     *
     * UI 側は本イベントを受領しても登録成功トースト・シートクローズは抑止しない
     * （Req 3.1）。またモーダルダイアログや全画面エラーを出さず、非ブロッキングな
     * snackbar 等で軽量に提示する（Req 3.2）。drawer のフィードセクションに表示
     * されるエラー UI（#39 Requirement 2 経路）は本イベントとは独立に
     * `SubscriptionRepository.observeLoadState()` 経由で駆動される。
     */
    data object SubscriptionRefreshFailed : RegisterFeedEvent
}
