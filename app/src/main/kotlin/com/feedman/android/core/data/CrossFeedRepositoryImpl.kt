package com.feedman.android.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.feedman.android.core.model.CrossFeedItem
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.network.paging.CursorPage
import com.feedman.android.core.network.paging.CursorPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CrossFeedRepository] の本実装（Issue #32）。
 *
 * [FeedmanApi.getCrossFeed] を [CursorPagingSource] 経由で呼び出し、初回レスポンスの
 * `since_time` をセッション中保持して後続ページリクエストに付与する。Pager の refresh
 * （= [PagingSource] 再生成）で保持値はリセットされ、次回初回レスポンスから再固定する。
 *
 * ## スレッド安全性
 *
 * [currentSinceTime] は単一の [PagingSource] インスタンス内からのみ書き換えられ、Paging 3
 * は同一 PagingSource に対して `load()` を直列化する（Paging 3 公式契約）。複数の PagingSource
 * を同時並行で動かす運用は本リポジトリでは想定しない（Pager は通常 1 つの downstream に対し
 * 1 つの PagingSource を生かす）。
 *
 * ## DI
 *
 * `RepositoryModule` で [CrossFeedRepository] にバインドされる（Hilt @Binds）。`FakeItemRepository`
 * （Issue #1 のモック実装）とは別系統で、本リポジトリは実 API 用の Pager 提供に専念する。
 *
 * @property api Retrofit ベースの Feedman API クライアント
 */
@Singleton
class CrossFeedRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
) : CrossFeedRepository {

    /** ページサイズ（SPEC §4.2: 50 件/回・上限 200）。Req 1.1 / 2.4。 */
    private val pageSize: Int = PAGE_SIZE

    /**
     * セッション中固定の `since_time`。初回レスポンスで採用し、リフレッシュで破棄する。
     *
     * 公開 getter は [currentSinceTime]。書き込みは PagingSource の load() 内のみで行われ、
     * 後続ページ取得が失敗した場合でも値を破棄しない（Req 5.3）。
     */
    @Volatile
    private var sessionSinceTime: String? = null

    override val currentSinceTime: String?
        get() = sessionSinceTime

    override fun pagingData(): Flow<PagingData<CrossFeedItem>> {
        // Pager は pagingSourceFactory を毎回呼び出して新しい PagingSource を作る。
        // リフレッシュ（invalidate）のたびに新しい session 用 PagingSource が生成され、
        // sessionSinceTime が新初回レスポンスで再固定される。
        val pager = Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false,
                initialLoadSize = pageSize,
            ),
            pagingSourceFactory = { newPagingSource() },
        )
        return pager.flow
    }

    /**
     * PagingSource 1 つを新規に生成する（= 新セッション開始）。
     *
     * Req 4.1 に従い、新セッション開始時に保持中の `sessionSinceTime` を破棄する。
     * 既存セッションの後続ページ取得が継続中に呼ばれた場合、その時点で進行中のロード
     * 結果は使われない（Paging 3 の invalidate 規約）。
     */
    internal fun newPagingSource(): PagingSource<String, CrossFeedItem> {
        // Req 4.1: リフレッシュ時にセッション保持値を破棄する。
        sessionSinceTime = null
        return CursorPagingSource(loader = ::loadPage)
    }

    /**
     * [CursorPagingSource] の loader 本体。
     *
     * - cursor が `null` のとき = 初回ロード。`since_time` は送らず、レスポンスの
     *   `since_time` を [sessionSinceTime] に固定する（Req 1.1 / 1.2 / 4.2）。
     * - cursor が非 null のとき = 後続ロード。保持中の [sessionSinceTime] を `since_time`
     *   クエリに付与する（Req 2.1）。レスポンスの `since_time` で上書きしない（Req 2.2）。
     * - 初回レスポンスの `since_time` が空文字列の場合は [FeedmanException]
     *   （CODE_UNKNOWN_ERROR）を throw する（Req 1.4）。
     * - 後続ページ失敗時に [sessionSinceTime] を破棄しない（Req 5.3）。
     *   ※ [CursorPagingSource] が例外を `LoadResult.Error` に包んでくれるため、本 loader
     *   では throw するだけでよい。
     */
    private suspend fun loadPage(cursor: String?): CursorPage<CrossFeedItem> {
        val response = if (cursor == null) {
            // Req 1.1: 初回は cursor / since_time なし、limit=50 で呼び出す。
            api.getCrossFeed(cursor = null, limit = pageSize, sinceTime = null)
        } else {
            // Req 2.1: 後続は保持中の since_time を付与する。
            api.getCrossFeed(cursor = cursor, limit = pageSize, sinceTime = sessionSinceTime)
        }

        if (cursor == null) {
            // Req 1.4: 初回 since_time 欠落（空文字列）はエラー扱い。kotlinx.serialization 側で
            // 非 nullable として宣言しているため key 欠落はパース時に例外になるが、空文字列で
            // 返ってきた場合のガードはここで実施する。
            if (response.sinceTime.isEmpty()) {
                throw FeedmanException(
                    code = FeedmanException.CODE_UNKNOWN_ERROR,
                    errorMessage = "cross-feed の初回レスポンスに since_time が含まれていません",
                )
            }
            // Req 1.2: セッション保持値として固定する。
            sessionSinceTime = response.sinceTime
        }
        // Req 2.2: 後続ページのレスポンス since_time では上書きしない（何もしない）。

        // Req 3.1 / 3.2: has_more / next_cursor の終端判定は CursorPagingSource に委譲。
        return CursorPage(
            items = response.items,
            nextCursor = response.nextCursor,
            hasMore = response.hasMore,
        )
    }

    companion object {
        /** SPEC §4.2: 50 件/回（Req 1.1 / Req 2.4）。 */
        const val PAGE_SIZE: Int = 50
    }
}
