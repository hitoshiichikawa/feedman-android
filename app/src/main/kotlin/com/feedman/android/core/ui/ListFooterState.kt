package com.feedman.android.core.ui

import androidx.paging.LoadState

/**
 * 無限スクロール一覧のフッター描画状態（Issue #28 / Req 1.2, 3.4, 4.1, 4.2 / NFR 3.1）。
 *
 * 一覧画面では「追加読込中」「追加エラー」「終端」「フッターなし」の 4 状態を
 * 排他的に描画する必要があり（Req 4.2）、この状態判定は UI 描画と分離して
 * JVM 単体テストで検証できる純粋関数として提供する（NFR 3.1）。
 *
 * 状態の優先順位（同時には現れない / Req 4.2 を保証）:
 * 1. [Loading]  — 追加ページの読み込みが進行中であれば最優先
 * 2. [Error]    — 追加ページの読み込みがエラー終了
 * 3. [EndOfList]— ページネーション終端到達
 * 4. [None]     — それ以外（フッター描画なし）
 *
 * 「追加読込中」と「追加エラー」が同時に true で渡されることは Paging 3 の仕様上
 * ないが、防御的に Loading を優先する（処理中であれば終端でもエラーでもないため）。
 */
sealed interface ListFooterState {
    /** 追加ページの読み込み中。リスト末尾にインジケータを表示する（Req 1.2）。 */
    data object Loading : ListFooterState

    /** 追加ページの読み込みがエラー終了。フッター型エラー表示を出す（Req 3.4）。 */
    data object Error : ListFooterState

    /** ページネーション終端。「最後まで読みました」を表示する（Req 4.1）。 */
    data object EndOfList : ListFooterState

    /** フッター描画なし（通常の続行状態）。 */
    data object None : ListFooterState
}

/**
 * フッター状態判定（Issue #28 / Req 4.2 を機械的に検証可能な単位として提供）。
 *
 * @param isAppendLoading 追加ページの読み込みが進行中（Paging 3 の `LoadState.Loading`）
 * @param isAppendError 追加ページの読み込みがエラー終了（`LoadState.Error`）
 * @param isEndOfPagination ページネーション終端（`LoadState.NotLoading(endOfPaginationReached = true)`）
 * @return 排他的に決まる [ListFooterState]
 */
fun resolveListFooterState(
    isAppendLoading: Boolean,
    isAppendError: Boolean,
    isEndOfPagination: Boolean,
): ListFooterState = when {
    isAppendLoading -> ListFooterState.Loading
    isAppendError -> ListFooterState.Error
    isEndOfPagination -> ListFooterState.EndOfList
    else -> ListFooterState.None
}

// ────────────────────────────────────────────────────────────────────────────
// Issue #34: タイムライン画面レベルの状態解決
// ────────────────────────────────────────────────────────────────────────────

/**
 * 横断タイムライン画面の「画面全体」状態（Issue #34 / Req 2.1, 2.4, 2.5, 3.1, 5.1, 6.1）。
 *
 * 画面全体（フルスクリーン領域を占有する）状態は次の 4 つを排他的に決定する:
 *
 * 1. [InitialLoading] — 初回ロード中で表示すべき記事が無い（Req 3.1）
 * 2. [InitialError]   — 初回ロード失敗（Req 3.2）
 * 3. [Empty]          — 取得 0 件かつ追加読込なし（Req 6.1）
 * 4. [Content]        — 通常一覧表示
 *
 * NFR 2.1 を満たすため、画面全体状態と一覧表示状態は同時に提示しない。フッタ状態は
 * [Content] のときのみ [resolveListFooterState] により付随的に決まる。
 *
 * pull-to-refresh の進行は本「画面全体状態」とは独立で、すでに `Content` のときは
 * 上部 indicator として描画される（Req 1.2 / 1.3）。refresh エラーは一覧を破壊せず
 * snackbar 通知（Req 5.1 / 5.2）に委ねるため、refresh が `Error` でも `itemCount > 0`
 * なら [Content] のままとなる（Req 5.3）。
 */
sealed interface TimelineScreenState {
    /** Req 3.1: 初回ロード中で表示すべき記事がまだ無い。 */
    data object InitialLoading : TimelineScreenState

    /** Req 3.2: 初回ロード失敗（再試行ボタンを画面全体で提示）。 */
    data object InitialError : TimelineScreenState

    /** Req 6.1: 初回ロードが完了し取得 0 件。空状態メッセージを提示する。 */
    data object Empty : TimelineScreenState

    /** Req 1.3 / 2.x / 4.x / 6.3: 通常の一覧表示。フッタ状態は別途解決する。 */
    data object Content : TimelineScreenState
}

/**
 * 画面全体状態の判定（Issue #34 / Req 3.1, 3.2, 6.1, 5.3 / NFR 2.1）。
 *
 * refresh の [LoadState] と現在保持されている記事件数・ページング終端フラグから、
 * 排他的に [TimelineScreenState] を決定する純粋関数。Composable / Paging に依存せず
 * JVM 単体テストで網羅検証できる。
 *
 * 判定優先順位:
 * 1. すでに記事を表示している場合（`itemCount > 0`）は常に [TimelineScreenState.Content]
 *    （Req 5.3 / 4.2: 既に読めている一覧を壊さない）。refresh が Error / Loading でも
 *    本判定は Content を返し、上層が refresh indicator や snackbar の責務を負う
 * 2. 記事 0 件 + refresh が [LoadState.Loading] → [InitialLoading]（Req 3.1）
 * 3. 記事 0 件 + refresh が [LoadState.Error]   → [InitialError]（Req 3.2）
 * 4. 記事 0 件 + refresh が [LoadState.NotLoading] → [Empty]（Req 6.1）
 *
 * @param refresh `LazyPagingItems.loadState.refresh`
 * @param itemCount `LazyPagingItems.itemCount`
 * @return 画面全体の排他状態
 */
fun resolveTimelineScreenState(
    refresh: LoadState,
    itemCount: Int,
): TimelineScreenState {
    if (itemCount > 0) {
        // Req 5.3 / 4.2: 既に表示している一覧を refresh エラーや進行中状態で破壊しない。
        return TimelineScreenState.Content
    }
    return when (refresh) {
        is LoadState.Loading -> TimelineScreenState.InitialLoading
        is LoadState.Error -> TimelineScreenState.InitialError
        is LoadState.NotLoading -> TimelineScreenState.Empty
    }
}
