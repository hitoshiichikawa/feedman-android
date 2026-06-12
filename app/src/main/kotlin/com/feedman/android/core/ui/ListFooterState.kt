package com.feedman.android.core.ui

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
