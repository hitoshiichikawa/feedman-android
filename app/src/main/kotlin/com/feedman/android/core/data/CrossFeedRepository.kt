package com.feedman.android.core.data

import androidx.paging.PagingData
import com.feedman.android.core.model.CrossFeedItem
import kotlinx.coroutines.flow.Flow

/**
 * 横断新着タイムライン（SPEC §5.1）のデータ層境界（Issue #32）。
 *
 * `GET /api/items/cross-feed`（SPEC §4.2）を [androidx.paging.Pager] で読み出す `Flow<PagingData>`
 * を提供し、初回レスポンスで返る `since_time` をセッション中固定して以降のページ取得に
 * 引き継ぐ。タイムライン UI（#33）・状態更新（#38）はスコープ外。
 *
 * ## セッション保持の規約
 *
 * - 「セッション」= 1 つの [androidx.paging.PagingSource] 生存期間（= [pagingData] の
 *   下流が 1 つの [PagingData] を保持し続けている期間）。
 * - 初回（cursor=null）レスポンスの `since_time` を [currentSinceTime] に保持し、以降の
 *   後続ページリクエストに `cursor` と共に付与する（Req 2.1 / 2.2）。
 * - Pager リフレッシュ（PagingSource 再生成）で [currentSinceTime] と保持 cursor は破棄され、
 *   次の初回レスポンスから新たに固定する（Req 4.1 / 4.2 / 4.3）。
 *
 * ## エラー伝播
 *
 * 非 2xx / I/O 失敗は [com.feedman.android.core.network.FeedmanException] が
 * [CursorPagingSource] 経由で `LoadResult.Error` として露出する（Req 5.1 / 5.2）。
 * 後続ページ取得が失敗した場合でも [currentSinceTime] は破棄されない（Req 5.3）。
 */
interface CrossFeedRepository {

    /**
     * 横断新着タイムラインの Paging データストリーム。
     *
     * 戻り値は `Flow<PagingData<CrossFeedItem>>` で、`cachedIn(...)` などで購読者側が
     * 共有するのが想定使用法。本メソッドの 1 回の呼び出しが 1 セッションに相当し、
     * 内部の [PagingSource] が再生成されるたびに `since_time` セッション保持値は
     * リセットされる（Req 4.1 / 4.2）。
     */
    fun pagingData(): Flow<PagingData<CrossFeedItem>>

    /**
     * 現在セッションで保持中の `since_time`（RFC3339 文字列）。初回レスポンス未到達時 /
     * リフレッシュ直後は `null`（Req 4.1）。
     *
     * テストから observable な状態として公開する（NFR 1.2）。
     */
    val currentSinceTime: String?
}
