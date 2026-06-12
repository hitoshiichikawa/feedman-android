package com.feedman.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * カーソルページネーション共通レスポンス envelope（SPEC §4.1）。
 *
 * `GET /api/feeds/{id}/items` / `/api/feeds/starred/items` / `/api/items/search` などの
 * 一覧系エンドポイントが共通で返す形。横断新着のみ別途 [CrossFeedPage] を使う
 * （`since_time` を保持するため）。
 *
 * @property items 当該ページのアイテム配列。
 * @property nextCursor 次ページ取得用の不透明トークン。終端では `null`。
 * @property hasMore `false` または `nextCursor == null` で終端を意味する。
 */
@Serializable
data class Page<T>(
    @SerialName("items") val items: List<T>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean,
)

/**
 * 横断新着タイムライン専用のページ envelope（SPEC §4.1）。
 *
 * `GET /api/items/cross-feed` のレスポンス本体。共通の `items` / `next_cursor` /
 * `has_more` に加えて `since_time`（RFC3339）を保持する。`since_time` はセッション初回の
 * 値を固定し以降のページ取得に使う（要件 1.3、SPEC §4.1）。
 *
 * 共通 [Page] を継承させずに専用 data class を切る理由:
 *   - kotlinx.serialization の data class はジェネリック継承よりも flat な定義の方が
 *     decode 仕様が明示的で曖昧さが少ない
 *   - 呼び出し側は cross-feed 専用 Pager で扱うため `Page<CrossFeedItem>` との
 *     interchangeability は不要
 */
@Serializable
data class CrossFeedPage(
    @SerialName("items") val items: List<CrossFeedItem>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("since_time") val sinceTime: String,
)
