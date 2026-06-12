package com.feedman.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 横断新着タイムラインの 1 記事（SPEC §4.2 `CrossFeedItem`）。
 *
 * `GET /api/items/cross-feed` のレスポンス `items[]` に格納される。フィード横断ビュー用に
 * フィード情報（`feed_id` / `feed_title` / `feed_favicon_url`）を含む点が特徴。
 *
 * @property feedFaviconUrl `data:<mime>;base64,...` 形式の data URL もしくは `null`
 *   （SPEC §4.4）。`null` 時は呼び出し側でレターアバターにフォールバックする。
 * @property isDateEstimated 配信日時がフィード側で推定値の場合 `true`。UI では「(推定)」表示の根拠。
 */
@Serializable
data class CrossFeedItem(
    @SerialName("id") val id: String,
    @SerialName("feed_id") val feedId: String,
    @SerialName("feed_title") val feedTitle: String,
    @SerialName("feed_favicon_url") val feedFaviconUrl: String? = null,
    @SerialName("title") val title: String,
    @SerialName("link") val link: String,
    @SerialName("summary") val summary: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("is_date_estimated") val isDateEstimated: Boolean,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("is_starred") val isStarred: Boolean,
    @SerialName("hatebu_count") val hatebuCount: Int,
)

/**
 * フィード別記事一覧の 1 記事（SPEC §4.2 `ItemSummary`）。
 *
 * `GET /api/feeds/{id}/items` のレスポンス `items[]` で返る。フィード単位画面で使うため
 * `feed_id` のみ含み、`feed_title` / `feed_favicon_url` は持たない。
 *
 * @property hatebuFetchedAt はてブ件数の最終取得時刻（RFC3339）。未取得時は `null`。
 */
@Serializable
data class ItemSummary(
    @SerialName("id") val id: String,
    @SerialName("feed_id") val feedId: String,
    @SerialName("title") val title: String,
    @SerialName("link") val link: String,
    @SerialName("summary") val summary: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("is_date_estimated") val isDateEstimated: Boolean,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("is_starred") val isStarred: Boolean,
    @SerialName("hatebu_count") val hatebuCount: Int,
    @SerialName("hatebu_fetched_at") val hatebuFetchedAt: String? = null,
)

/**
 * 記事詳細（SPEC §4.2 `ItemDetail`）。
 *
 * `GET /api/items/{id}` のレスポンス本体。`ItemSummary` の全フィールドに加えて
 * sanitized HTML 本文 `content` と `author` を含む。`ItemSummary` を継承させると
 * Kotlin の data class 制約に抵触するため、フィールドを並列に保持する独立 data class
 * として表現する。
 */
@Serializable
data class ItemDetail(
    @SerialName("id") val id: String,
    @SerialName("feed_id") val feedId: String,
    @SerialName("title") val title: String,
    @SerialName("link") val link: String,
    @SerialName("summary") val summary: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("is_date_estimated") val isDateEstimated: Boolean,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("is_starred") val isStarred: Boolean,
    @SerialName("hatebu_count") val hatebuCount: Int,
    @SerialName("hatebu_fetched_at") val hatebuFetchedAt: String? = null,
    @SerialName("content") val content: String,
    @SerialName("author") val author: String,
)

/**
 * スター一覧の 1 記事（SPEC §4.2 `StarredItemSummary`）。
 *
 * `GET /api/feeds/starred/items` のレスポンス `items[]` で返る。`ItemSummary` の
 * 全フィールドに加えて、ソース表示のための `feed_title` を含む。
 */
@Serializable
data class StarredItemSummary(
    @SerialName("id") val id: String,
    @SerialName("feed_id") val feedId: String,
    @SerialName("title") val title: String,
    @SerialName("link") val link: String,
    @SerialName("summary") val summary: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("is_date_estimated") val isDateEstimated: Boolean,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("is_starred") val isStarred: Boolean,
    @SerialName("hatebu_count") val hatebuCount: Int,
    @SerialName("hatebu_fetched_at") val hatebuFetchedAt: String? = null,
    @SerialName("feed_title") val feedTitle: String,
)

/**
 * 横断検索結果の 1 記事（SPEC §4.2 `ItemSearchHit`）。
 *
 * `GET /api/items/search` の `items[]` で返る。`ItemSummary` とは差分があり、
 * `hatebu_fetched_at` を含まず、`feed_title` を含み、`favicon_url` と `published_at` が
 * nullable な点に注意する（要件 1.5）。
 *
 * @property publishedAt 検索結果ではバックエンドが推定不能な場合 `null` を返す。
 * @property faviconUrl SPEC §4.4 の data URL もしくは `null`。
 */
@Serializable
data class ItemSearchHit(
    @SerialName("id") val id: String,
    @SerialName("feed_id") val feedId: String,
    @SerialName("title") val title: String,
    @SerialName("link") val link: String,
    @SerialName("summary") val summary: String,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("is_date_estimated") val isDateEstimated: Boolean,
    @SerialName("hatebu_count") val hatebuCount: Int,
    @SerialName("feed_title") val feedTitle: String,
    @SerialName("favicon_url") val faviconUrl: String? = null,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("is_starred") val isStarred: Boolean,
)
