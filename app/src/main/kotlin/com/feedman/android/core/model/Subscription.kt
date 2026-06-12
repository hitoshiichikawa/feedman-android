package com.feedman.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 購読しているフィード 1 件（SPEC §4.2 `Subscription`）。
 *
 * `GET /api/subscriptions` のレスポンス `items[]` 等で返る。サイドバー（ドロワー）の
 * フィード一覧と購読設定シートで利用する。
 *
 * @property faviconUrl SPEC §4.4 の data URL もしくは `null`。
 * @property errorMessage `feed_status == "error"` のときのみ意味を持つエラー説明。
 * @property feedStatus `"active"` / `"stopped"` / `"error"` の 3 値。型は将来 enum 化する
 *   余地を残すため現時点では文字列のまま保持する（要件 1.4 に従い field 名・nullable 性を
 *   SPEC から逸脱させない）。
 */
@Serializable
data class Subscription(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("feed_id") val feedId: String,
    @SerialName("feed_title") val feedTitle: String,
    @SerialName("feed_url") val feedUrl: String,
    @SerialName("favicon_url") val faviconUrl: String? = null,
    @SerialName("fetch_interval_minutes") val fetchIntervalMinutes: Int,
    @SerialName("feed_status") val feedStatus: String,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("unread_count") val unreadCount: Int,
    @SerialName("created_at") val createdAt: String,
)
