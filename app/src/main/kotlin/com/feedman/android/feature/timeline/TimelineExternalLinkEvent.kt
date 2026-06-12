package com.feedman.android.feature.timeline

/**
 * タイムライン外部リンク起動の one-shot 通知イベント（Issue #37 / Req 2.4 / Req 3.3 / Req 4.1 / Req 4.2）。
 *
 * [TimelineViewModel] の `externalLinkEvents` SharedFlow 経由で UI へ流れる。再コンポジションで
 * 再送されないように `replay = 0` で公開する（[ArticleDetailEvent] と同じ規約）。
 */
sealed class TimelineExternalLinkEvent {

    /**
     * 外部リンク起動成功後の既読化（`ItemDetailRepository.updateState(isRead=true)`）
     * サーバー反映が失敗した（Req 2.4）。
     */
    data object MarkReadFailed : TimelineExternalLinkEvent()

    /**
     * URL バリデーション失敗（InvalidUrl）または対応アプリ不在（NoAppToHandle）で
     * 外部リンクが開けなかった（Req 3.3 / Req 4.1 / Req 4.2）。
     */
    data object OpenLinkFailed : TimelineExternalLinkEvent()
}
