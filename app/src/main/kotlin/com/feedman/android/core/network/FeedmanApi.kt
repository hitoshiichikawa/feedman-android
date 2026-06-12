package com.feedman.android.core.network

import com.feedman.android.core.model.CrossFeedItem
import com.feedman.android.core.model.CrossFeedPage
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.model.ItemSearchHit
import com.feedman.android.core.model.ItemSummary
import com.feedman.android.core.model.Page
import com.feedman.android.core.model.StarredItemSummary
import com.feedman.android.core.model.Subscription
import com.feedman.android.core.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Feedman REST API の Retrofit インターフェース（SPEC §4.2 を正本として転記）。
 *
 * すべてのメソッドは `suspend` で宣言され、2xx 応答は `core/model` の `@Serializable` データ
 * クラスへ kotlinx.serialization で decode される。非 2xx / I/O 失敗は
 * [FeedmanException]（RuntimeException）として throw される（Req 3.x）— 変換層の責務は
 * [FeedmanErrorMappingInterceptor] と [FeedmanApiCallAdapter] が担う。
 *
 * 認証ヘッダ付与（Issue #21）・401 自動リフレッシュ（Issue #22）・Paging 3 基盤（Issue #18）は
 * **本インターフェース自身は知らない**: 追加 interceptor / authenticator は
 * [ApiClientFactory] の引数として外部から注入する設計（Req 4）。
 *
 * @see ApiClientFactory FeedmanApi の生成と OkHttp/Retrofit/Json 構成
 */
interface FeedmanApi {

    // ---- 認証 / ユーザー ----------------------------------------------------

    /**
     * 現在ログイン中のユーザー情報を取得する（SPEC §4.2 GET `/auth/me`）。
     */
    @GET("auth/me")
    suspend fun getCurrentUser(): User

    /**
     * ログアウト（サーバー側セッション破棄）。SPEC §4.2 POST `/auth/logout`。
     */
    @POST("auth/logout")
    suspend fun logout()

    /**
     * 退会（全購読・状態を削除）。SPEC §4.2 DELETE `/api/users/me`。
     */
    @DELETE("api/users/me")
    suspend fun deleteCurrentUser()

    /**
     * 横断一覧の最終閲覧時刻を更新（新着バッジ計算用）。
     * SPEC §4.2 PUT `/api/users/me/cross-feed-last-seen`。
     */
    @PUT("api/users/me/cross-feed-last-seen")
    suspend fun updateCrossFeedLastSeen()

    // ---- 横断新着タイムライン ----------------------------------------------

    /**
     * 全フィード横断の新着タイムライン（SPEC §4.2 GET `/api/items/cross-feed`）。
     *
     * @param cursor 次ページ取得用の不透明トークン。初回は `null`。
     * @param limit ページサイズ。null の場合サーバー既定（50）が適用される。
     * @param sinceTime セッション初回レスポンスの `since_time`（RFC3339）を後続ページに
     *   引き継いで送信するためのクエリ。SPEC §4.1 / §10 受け入れ基準第 2 項により
     *   無限スクロール中の新着判定基準時刻をセッション中固定するために使う。初回ロード時
     *   は `null` を渡し、後続ページ取得で同一値を維持する（Issue #32 Req 2.1 / 2.2）。
     */
    @GET("api/items/cross-feed")
    suspend fun getCrossFeed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("since_time") sinceTime: String? = null,
    ): CrossFeedPage

    // ---- 購読 ----------------------------------------------------------------

    /**
     * 購読一覧（サイドバー）。SPEC §4.2 GET `/api/subscriptions`。
     */
    @GET("api/subscriptions")
    suspend fun getSubscriptions(): List<Subscription>

    /**
     * 購読解除。SPEC §4.2 DELETE `/api/subscriptions/{id}`。
     */
    @DELETE("api/subscriptions/{id}")
    suspend fun deleteSubscription(@Path("id") subscriptionId: String)

    /**
     * フェッチ間隔等の購読設定更新。SPEC §4.2 PUT `/api/subscriptions/{id}/settings`。
     */
    @PUT("api/subscriptions/{id}/settings")
    suspend fun updateSubscriptionSettings(
        @Path("id") subscriptionId: String,
        @Body request: SubscriptionSettingsRequest,
    ): Subscription

    /**
     * 停止 / エラーフィードの再開。SPEC §4.2 POST `/api/subscriptions/{id}/resume`。
     */
    @POST("api/subscriptions/{id}/resume")
    suspend fun resumeSubscription(@Path("id") subscriptionId: String): Subscription

    /**
     * 手動フェッチ（= Pull-to-refresh の実体、同期）。
     * クールダウン中は `429 / FEED_COOLDOWN` を返す（SPEC §4.2）。
     */
    @POST("api/subscriptions/{id}/fetch")
    suspend fun fetchSubscription(@Path("id") subscriptionId: String): Subscription

    // ---- フィード ------------------------------------------------------------

    /**
     * フィード登録。URL 自動検出。専用レート制限あり。SPEC §4.2 POST `/api/feeds`。
     */
    @POST("api/feeds")
    suspend fun registerFeed(@Body request: RegisterFeedRequest): Subscription

    /**
     * フィード別記事一覧。`filter` で all / unread / starred を切替。
     * SPEC §4.2 GET `/api/feeds/{id}/items`。
     */
    @GET("api/feeds/{id}/items")
    suspend fun getFeedItems(
        @Path("id") feedId: String,
        @Query("filter") filter: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Page<ItemSummary>

    /**
     * 全フィード横断スター一覧。SPEC §4.2 GET `/api/feeds/starred/items`。
     */
    @GET("api/feeds/starred/items")
    suspend fun getStarredItems(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Page<StarredItemSummary>

    /**
     * フィード詳細。SPEC §4.2 GET `/api/feeds/{id}`。
     */
    @GET("api/feeds/{id}")
    suspend fun getFeed(@Path("id") feedId: String): Subscription

    /**
     * フィード URL 変更。v1 UI 対象外だが SPEC §4.2 に列挙されているため契約として宣言する。
     * PATCH `/api/feeds/{id}`。
     */
    @PATCH("api/feeds/{id}")
    suspend fun patchFeed(
        @Path("id") feedId: String,
        @Body request: PatchFeedRequest,
    ): Subscription

    /**
     * フィード削除。v1 UI 対象外（解除は購読側）。SPEC §4.2 DELETE `/api/feeds/{id}`。
     */
    @DELETE("api/feeds/{id}")
    suspend fun deleteFeed(@Path("id") feedId: String)

    // ---- 記事 ----------------------------------------------------------------

    /**
     * 記事詳細（`content` 含む）。SPEC §4.2 GET `/api/items/{id}`。
     */
    @GET("api/items/{id}")
    suspend fun getItem(@Path("id") itemId: String): ItemDetail

    /**
     * 既読 / スター更新。body の各フィールドは nullable で、null の場合は変更しない契約。
     * SPEC §4.2 PUT `/api/items/{id}/state`。
     */
    @PUT("api/items/{id}/state")
    suspend fun updateItemState(
        @Path("id") itemId: String,
        @Body request: ItemStateUpdateRequest,
    ): CrossFeedItem

    // ---- 検索 ----------------------------------------------------------------

    /**
     * 横断検索。`scope=global|feed`（デフォルト global）。SPEC §4.2 GET `/api/items/search`。
     */
    @GET("api/items/search")
    suspend fun searchItems(
        @Query("q") query: String,
        @Query("scope") scope: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): Page<ItemSearchHit>
}

// ---- リクエストボディ ----------------------------------------------------

/**
 * `PUT /api/subscriptions/{id}/settings` のリクエストボディ。
 *
 * SPEC §5.6 では fetch_interval_minutes（30/60/180/360）の更新が v1 の対象。
 * 各フィールドは nullable で、null のものは変更しない契約（部分更新）。
 */
@Serializable
data class SubscriptionSettingsRequest(
    @SerialName("fetch_interval_minutes") val fetchIntervalMinutes: Int? = null,
)

/**
 * `POST /api/feeds` のリクエストボディ。
 */
@Serializable
data class RegisterFeedRequest(
    @SerialName("url") val url: String,
)

/**
 * `PATCH /api/feeds/{id}` のリクエストボディ。v1 UI 対象外だが契約として宣言。
 */
@Serializable
data class PatchFeedRequest(
    @SerialName("feed_url") val feedUrl: String? = null,
)

/**
 * `PUT /api/items/{id}/state` のリクエストボディ（SPEC §4.2 / Req 1.4）。
 *
 * `is_read` / `is_starred` は nullable で、null のフィールドは更新しない契約。
 * kotlinx.serialization は null をそのままシリアライズする（`explicitNulls = true` 既定）ため、
 * フィールド省略を意図したい呼び出しでは null を明示的に渡す。
 */
@Serializable
data class ItemStateUpdateRequest(
    @SerialName("is_read") val isRead: Boolean? = null,
    @SerialName("is_starred") val isStarred: Boolean? = null,
)
