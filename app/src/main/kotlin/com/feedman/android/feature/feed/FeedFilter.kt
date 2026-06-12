package com.feedman.android.feature.feed

import com.feedman.android.core.data.FeedItemFilter

/**
 * フィード別画面の上部フィルタタブ（Issue #41 / Req 2.1 / 2.2 / 2.3 / 2.7）。
 *
 * design/mobile/fm-screens.jsx の FMFilterTabs に対応し、左から `すべて → 未読 → スター`
 * の 3 つを排他選択する。タブ状態はサーバ側の API クエリ ([FeedItemFilter]) と 1:1 対応
 * させるため、UI 列挙の `toFeedItemFilter()` で射影する。
 *
 * design 側の string id（"all" / "unread" / "starred"）は本 enum の [stableKey] と一致させ、
 * 永続化や savedState 保存時のキーとして使う（Req 2.7 で「直前選択を引き継がない」と
 * 規定されているため、現状は永続化しないが、将来の savedState 移行に備えて key を確保する）。
 */
enum class FeedFilter(val stableKey: String) {
    ALL("all"),
    UNREAD("unread"),
    STARRED("starred"),
    ;

    /**
     * サーバ API の `?filter=` 値（[FeedItemFilter]）へ射影する純粋関数。
     */
    fun toFeedItemFilter(): FeedItemFilter = when (this) {
        ALL -> FeedItemFilter.ALL
        UNREAD -> FeedItemFilter.UNREAD
        STARRED -> FeedItemFilter.STARRED
    }

    companion object {
        /** 初期表示時のデフォルト（Req 2.2: 「すべて」を選択状態として表示）。 */
        val DEFAULT: FeedFilter = ALL
    }
}
