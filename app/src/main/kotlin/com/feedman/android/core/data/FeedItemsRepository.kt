package com.feedman.android.core.data

import androidx.paging.PagingData
import com.feedman.android.core.model.ItemSummary
import kotlinx.coroutines.flow.Flow

/**
 * フィード別記事一覧（SPEC §5.2 / §4.2 `GET /api/feeds/{id}/items`）のデータ層境界（Issue #40）。
 *
 * 任意のフィード ID と [FeedItemFilter] を受け取り、Paging 3 の `Flow<PagingData<ItemSummary>>`
 * を返す。サーバー側の `?filter=all|unread|starred` クエリへ enum を 1:1 で射影し、フィルタ別の
 * カーソル方式ページングを Issue #18 の `core/network/paging` 基盤に委ねる。
 *
 * ## フィルタ変更時の挙動（Req 2）
 *
 * 同一 feedId で異なる filter を指定した [pagingData] を呼ぶたびに新しい [androidx.paging.Pager]
 * を返す設計とし、UI 側は filter ごとに pagingData を生成する。これにより、フィルタ変更時に
 * 自動的に先頭ページ（カーソル未指定）からの取得が開始され、直前のフィルタの蓄積が後続の
 * ページング状態に持ち込まれない（Req 2.1 / 2.2）。
 *
 * ## ページング基盤との整合（Req 3 / 4）
 *
 * - `next_cursor` / `has_more` の終端判定・エラー透過は [com.feedman.android.core.network.paging.CursorPagingSource]
 *   が担う（Req 3.1 / 3.2 / 3.3 / 3.4 / 3.5）
 * - リフレッシュ（PagingSource 再生成）は同一 filter のまま先頭ページから再取得される
 *   （Req 4.1 / 4.2）
 *
 * ## スコープ外
 *
 * - 画面実装 / Pull-to-refresh の手動フェッチ呼び出し / フィードステータスバナー
 *   （別 Issue #41 / #42 / 購読設定系）
 */
interface FeedItemsRepository {

    /**
     * 指定フィードの記事一覧 Paging データストリーム。
     *
     * 呼び出し 1 回ごとに新しい [androidx.paging.Pager] を生成する。UI 側は filter 切替時に
     * 本メソッドを呼び直し、戻り値の Flow を購読し直すことでフィルタ別の先頭ページ取得を
     * 起動する（Req 2.1 / 2.2）。同一 (feedId, filter) のまま購読を維持している間は、
     * Pager の refresh で先頭ページから取り直される（Req 4.1）。
     *
     * @param feedId 対象フィードの ID（SPEC §4.2 `GET /api/feeds/{id}/items` の `{id}`）。
     *   本リポジトリでは値を書き換えず、そのままパスに埋め込む（Req 1.4）
     * @param filter サーバーへ送る `?filter=` 値（Req 1.1 / 1.2 / 1.3）
     */
    fun pagingData(feedId: String, filter: FeedItemFilter): Flow<PagingData<ItemSummary>>
}

/**
 * フィード別記事一覧のフィルタ条件（Req 1.1 / 1.2 / 1.3）。
 *
 * サーバー API の `?filter=` クエリ文字列との 1:1 対応。文字列化は [queryValue] が担う。
 */
enum class FeedItemFilter(
    /** サーバー API の `?filter=` クエリ値。 */
    val queryValue: String,
) {
    /** すべての記事（Req 1.1）。 */
    ALL("all"),

    /** 未読のみ（Req 1.2）。 */
    UNREAD("unread"),

    /** スター付きのみ（Req 1.3）。 */
    STARRED("starred"),
}
