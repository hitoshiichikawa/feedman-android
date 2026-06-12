package com.feedman.android.core.model

/**
 * Minimal article snapshot used by the skeleton's mock timeline.
 *
 * Subsequent Issues will replace this with the full `CrossFeedItem` / `ItemSummary`
 * models defined in `design/SPEC.md` §4.2.
 *
 * @property id Stable identifier of the article.
 * @property title Article title shown in the timeline list.
 * @property feedName Source feed display name.
 * @property publishedAt Pre-formatted relative timestamp string (e.g. "3時間前").
 */
data class ItemSummary(
    val id: String,
    val title: String,
    val feedName: String,
    val publishedAt: String,
)
