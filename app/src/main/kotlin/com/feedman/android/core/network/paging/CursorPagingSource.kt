package com.feedman.android.core.network.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.feedman.android.core.network.FeedmanException
import java.io.IOException

/**
 * Feedman API のカーソルページネーション（SPEC §4.1）を Paging 3 に橋渡しする共通基盤。
 *
 * 4 種の一覧（横断新着 / フィード別 / スター / 検索）すべてが共通利用する想定で、画面・
 * エンドポイント固有のロジックは [loader] として外部から注入する（Req 5.1）。本クラス自身は
 * 次キー解決・終端判定・エラー伝播・リフレッシュ起点の規約のみを担い、特定の一覧種別を
 * 知らない。
 *
 * ## 設計上の判断
 *
 * - **Key 型は `String?`**: SPEC §4.1 の `next_cursor` は不透明文字列で、初回ロードは未指定
 *   （null）として扱うため、`String?` を採用した（Req 1.2 / Req 1.3）
 * - **`getRefreshKey` は常に `null`**: カーソル方式は anchor item から「ひとつ前のページの
 *   カーソル」を機械的に逆算する手段が無く（next_cursor は不透明トークンで前ページに
 *   戻せない）、Req 4.1（リフレッシュは先頭ページから取得を再開する）と整合させるため
 *   常に `null` を返す。これは Pager がリフレッシュ時に key=null で `load(Refresh)` を
 *   呼ぶ動作と同一になる
 * - **終端判定**: `hasMore == false` の場合に加え、`nextCursor` が `null` または空文字列の
 *   場合も終端として扱う（Req 2.1 / Req 2.2）。サーバーが矛盾した envelope（`has_more=true`
 *   かつ `next_cursor=null`）を返した場合でも、不正なループに陥らないよう保守側に倒す
 * - **エラー伝播**: [FeedmanException] と `IOException` を `LoadResult.Error` に詰めて
 *   そのまま返す（Req 3.1 / Req 3.2）。Paging 3 の `retry()` はこの error 結果と同じ
 *   `LoadParams.key`（= 失敗時のカーソル）で `load()` を再呼び出しするため、同じ位置の
 *   再要求が自動的に成立する（Req 3.3）。その他の Throwable は再 throw し、SDK 側の
 *   uncaught 経路に乗せる（silent fail 回避）
 *
 * @param loader 不透明カーソル（初回は null）を受け取り、当該ページの [CursorPage] を返す
 *   suspend 関数。Repository が `FeedmanApi` を呼び出し、戻り値 `Page<T>` / `CrossFeedPage`
 *   を [CursorPage] に詰め替えて渡す想定。`FeedmanException` / `IOException` はそのまま
 *   throw すること（本クラスが `LoadResult.Error` に変換する）。
 */
class CursorPagingSource<T : Any>(
    private val loader: suspend (cursor: String?) -> CursorPage<T>,
) : PagingSource<String, T>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, T> {
        // 初回ロード（Refresh かつ key=null）はカーソル未指定で取得する（Req 1.2）。
        // 追加ロード（Append）は前ページの next_cursor がそのまま key として渡る（Req 1.1 / 1.3）。
        val cursor: String? = params.key
        return try {
            val page = loader(cursor)
            LoadResult.Page(
                data = page.items,
                // カーソル方式では prevKey は使わない（refresh は常に先頭から / Req 4.1）。
                prevKey = null,
                nextKey = resolveNextKey(page),
            )
        } catch (e: FeedmanException) {
            // Req 3.1 / 3.2: API エラーは LoadResult.Error として露出（Paging が retry 起点に使う）。
            LoadResult.Error(e)
        } catch (e: IOException) {
            // Req 3.1: ネットワーク I/O 失敗も同様に Error として露出。
            LoadResult.Error(e)
        }
    }

    /**
     * 終端判定（Req 2.1 / Req 2.2）。
     *
     * - `hasMore == false` なら終端（next_cursor の値に関わらず）
     * - `nextCursor` が null または空文字列なら終端
     * - それ以外は当該不透明トークンをそのまま次の key とする（Req 1.3）
     */
    private fun resolveNextKey(page: CursorPage<T>): String? {
        if (!page.hasMore) return null
        val next = page.nextCursor
        if (next.isNullOrEmpty()) return null
        return next
    }

    /**
     * リフレッシュ起点の解決（Req 4.1）。
     *
     * カーソル方式は anchor item から前ページのカーソルを逆算できないため、常に `null` を
     * 返し、「リフレッシュ = 先頭ページ（カーソル未指定）から取得し直す」挙動に固定する。
     */
    override fun getRefreshKey(state: PagingState<String, T>): String? = null
}
