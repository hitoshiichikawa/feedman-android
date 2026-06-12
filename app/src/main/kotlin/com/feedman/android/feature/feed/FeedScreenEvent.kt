package com.feedman.android.feature.feed

/**
 * フィード別画面の one-shot 通知イベント（Issue #41 / Req 3.7 / 3.8）。
 *
 * `FeedScreenViewModel.events` SharedFlow 経由で UI 側に流す。再コンポジションで再送
 * されないよう `replay = 0` 規約を採用する（TimelineExternalLinkEvent / ArticleDetailEvent
 * と同じ流儀）。
 */
sealed class FeedScreenEvent {

    /**
     * Req 3.7: 再開処理が成功し、警告バナーが非表示化された後に流す通知。UI 側は
     * snackbar 表示と一覧の refresh（PagingSource invalidate）に使う。
     */
    data object ResumeSucceeded : FeedScreenEvent()

    /**
     * Req 3.8: 再開処理が失敗した。`message` には FeedmanException 由来 or フォールバック
     * 文言を入れる。UI 側で snackbar 表示する。
     *
     * @property message 失敗理由を含むメッセージ
     */
    data class ResumeFailed(val message: String) : FeedScreenEvent()

    /**
     * Issue #37 Req 4.x 流用: 外部リンク起動自体の失敗（InvalidUrl / NoAppToHandle）通知。
     * 既読化失敗は ItemStateStore.failures で別途流す。
     */
    data object OpenLinkFailed : FeedScreenEvent()

    /**
     * Issue #42 Req 2.1: 手動フェッチが成功応答を返した。UI 側は一覧 refresh（Paging
     * invalidate）に使う。ドロワー未読バッジは SubscriptionRepository 内部の
     * `_subscriptions` 更新によって observeSubscriptions Flow 経由で自動反映される
     * （Req 2.3）。
     */
    data object FetchSucceeded : FeedScreenEvent()

    /**
     * Issue #42 Req 3.1 / 3.2 / 3.3: 手動フェッチがクールダウン理由で拒否された。
     *
     * UI 側は `retryAfterSeconds` の有無で文言を切り替える:
     * - 値が非 null のとき: 残り秒数を含む snackbar（Req 3.2）
     * - 値が null のとき: 残り秒数を明示しないクールダウン中文言（Req 3.3）
     *
     * @property retryAfterSeconds サーバーが応答した残り秒数。欠落時は null。
     */
    data class FetchCooldown(val retryAfterSeconds: Int?) : FeedScreenEvent()

    /**
     * Issue #42 Req 4.1 / 4.3: 手動フェッチがクールダウン以外の理由で失敗した。
     * UI 側は本イベントで snackbar 表示する。`message` には FeedmanException 由来 or
     * ネットワーク失敗時のフォールバック文言を入れる。
     *
     * @property message 失敗理由を含むメッセージ
     */
    data class FetchFailed(val message: String) : FeedScreenEvent()
}
