package com.feedman.android.feature.search

/**
 * 検索画面の外部リンク起動 one-shot 通知イベント（Issue #48 / Req 2.4）。
 *
 * [SearchViewModel] の `externalLinkEvents` SharedFlow 経由で UI へ流れる。再コンポジションで
 * 再送されないように `replay = 0` で公開する（[com.feedman.android.feature.timeline.TimelineExternalLinkEvent]
 * と同じ規約）。
 */
sealed class SearchExternalLinkEvent {

    /**
     * URL バリデーション失敗（InvalidUrl）または対応アプリ不在（NoAppToHandle）で
     * 外部リンクが開けなかった（Req 2.4）。既読化トリガーが取り消されたことの裏返し。
     */
    data object OpenLinkFailed : SearchExternalLinkEvent()
}
