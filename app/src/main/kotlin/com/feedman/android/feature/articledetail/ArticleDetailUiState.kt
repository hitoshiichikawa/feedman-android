package com.feedman.android.feature.articledetail

import com.feedman.android.core.model.ItemDetail

/**
 * 記事詳細シートの UI 状態（Issue #36 / Req 1, 3, 4, 5, 6）。
 *
 * `ArticleDetailViewModel` が `StateFlow<ArticleDetailUiState>` として公開し、Composable は
 * stateless に受け取って描画する（CLAUDE.md の Kotlin / Android 規約に従う）。
 *
 * ## 状態
 *
 * - [Hidden]: シートが起動していない初期状態。
 * - [Loading]: シートを開いた直後で `getItem(id)` 取得中（Req 6.1）。
 * - [Content]: 取得成功して本文を表示中（Req 1, 2, 5）。`isRead` / `isStarred` は楽観的更新で
 *   即時反映される（Req 3, 4）。
 * - [Error]: 取得失敗（Req 6.2）。再試行可能（Req 6.3）。
 *
 * ## ロード中のスター・既読は Content に閉じる
 *
 * Loading 状態では本文／スター／既読の表示要素を保持しない。シートを開いた瞬間の既読化
 * （Req 3.1）はサーバーから ItemDetail が返ってきた時点で `Content.isRead=true` として反映する
 * （詳細は ViewModel 実装を参照）。
 */
sealed class ArticleDetailUiState {

    /** シート未起動状態。Composable は `Hidden` のときシート Composable を描画しない。 */
    data object Hidden : ArticleDetailUiState()

    /** シート起動直後 / 再試行直後の取得中（Req 6.1）。 */
    data class Loading(val itemId: String) : ArticleDetailUiState()

    /**
     * 取得成功して本文を表示中（Req 1, 2, 5）。
     *
     * @property detail 取得した [ItemDetail] のスナップショット（`isRead` / `isStarred` を除く
     *           表示用フィールドの正本）。
     * @property isRead 楽観的更新を含む現在の既読状態。Req 3.4 — シート上の既読目印描画に使う。
     * @property isStarred 楽観的更新を含む現在のスター状態。Req 4.4 / 4.6 — フッタと本文上部の
     *           スターアイコンを同じ状態として描画する。
     */
    data class Content(
        val detail: ItemDetail,
        val isRead: Boolean,
        val isStarred: Boolean,
    ) : ArticleDetailUiState()

    /**
     * 取得失敗状態（Req 6.2）。
     *
     * @property itemId 失敗時の itemId。再試行（Req 6.3）でこの ID を使って再取得する。
     * @property message ユーザー可視メッセージ。`FeedmanException.errorMessage` をそのまま採用する。
     */
    data class Error(val itemId: String, val message: String) : ArticleDetailUiState()
}

/**
 * ViewModel から UI へ送る one-shot 通知イベント（Issue #36 / Req 3.3 / 4.5）。
 *
 * 楽観的更新の失敗を snackbar で 1 度だけ表示するために、状態とは別ストリームで流す
 * （再コンポジションで何度も発火しない）。SharedFlow `replay = 0` を採用する想定。
 *
 * @property message snackbar に表示する文言（resource 文字列を ViewModel 側で resolve せず、
 *           UI 側で resolve するために `messageResId` ではなく resolved 文字列を渡す
 *           shouldn't… ここは UI 側で stringResource() を経由する設計のため、ViewModel は
 *           「失敗種別」のみを通知し UI 側で文言を選ぶ）。
 */
sealed class ArticleDetailEvent {
    /** 既読化サーバー反映が失敗した（Req 3.3）。 */
    data object MarkReadFailed : ArticleDetailEvent()

    /** スター更新サーバー反映が失敗した（Req 4.5）。 */
    data object StarUpdateFailed : ArticleDetailEvent()
}
