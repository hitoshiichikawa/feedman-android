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
}
