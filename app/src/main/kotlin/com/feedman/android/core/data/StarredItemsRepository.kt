package com.feedman.android.core.data

import androidx.paging.PagingData
import com.feedman.android.core.model.StarredItemSummary
import kotlinx.coroutines.flow.Flow

/**
 * スター一覧（SPEC §5.3 / §4.2 `GET /api/feeds/starred/items`）のデータ層境界（Issue #46）。
 *
 * 全フィード横断のスター記事一覧を Paging 3 の `Flow<PagingData<StarredItemSummary>>` として
 * 公開する。各記事は `feed_title` を保持しており、UI 側でソース表示に用いる
 * （Req 1.4 / NFR 2.3）。横断検索（#47）はスコープ外（Req 6.2）。
 *
 * ## ページング規約（Req 2）
 *
 * - 初回ロード（cursor=null）はクエリ未指定で先頭ページを取得する（Req 2.1）
 * - 後続ロードは前ページの `next_cursor` を `cursor` クエリに搬送する（Req 2.2）
 * - `next_cursor=null` / `has_more=false` で終端に到達したとみなす（Req 2.3 / 2.4）
 * - 初回失敗・追加ロード失敗はいずれも [com.feedman.android.core.network.paging.CursorPagingSource]
 *   経由で `LoadResult.Error` として上位レイヤーへ透過する（Req 2.5 / 2.6）
 *
 * ## リフレッシュ規約（Req 3）
 *
 * Pull-to-refresh は `LazyPagingItems.refresh()` 経由で Pager の [androidx.paging.PagingSource]
 * を再生成し、新セッションとして先頭ページから取得し直す。蓄積中のページは破棄される
 * （Req 3.2）。リフレッシュ後の終端判定・エラー透過は通常ロードと同じ規則で扱う
 * （Req 3.3 / 3.4）。
 *
 * ## スコープ外
 *
 * - スター一覧画面 UI（[feature/starred] 配下） — 別ファイル
 * - 楽観的更新オーバーレイ合成 — [ItemStateStore] / 画面 ViewModel 側で行う
 * - フィード内検索 UI / 既読・未読フィルタ UI（Req 6.2 / 6.3）
 */
interface StarredItemsRepository {

    /**
     * スター一覧の Paging データストリーム。
     *
     * 呼び出し 1 回ごとに新しい [androidx.paging.Pager] を生成する。UI 側は
     * `cachedIn(viewModelScope)` で購読し、`LazyPagingItems.refresh()` でセッションを
     * 切り替える。
     */
    fun pagingData(): Flow<PagingData<StarredItemSummary>>
}
