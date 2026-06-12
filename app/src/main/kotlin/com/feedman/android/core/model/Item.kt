package com.feedman.android.core.model

/**
 * Minimal article snapshot used by the skeleton's mock timeline.
 *
 * This is the mock-only type introduced by Issue #1 and kept intentionally separate
 * from the SPEC §4.2 API domain models added in Issue #15 (`ItemSummary` etc.).
 * The integration / replacement policy between this mock type and the API models is
 * deferred to a subsequent Issue per requirements.md "Out of Scope".
 *
 * @property id Stable identifier of the article.
 * @property title Article title shown in the timeline list.
 * @property feedName Source feed display name.
 * @property publishedAt Pre-formatted relative timestamp string (e.g. "3時間前").
 */
data class MockTimelineItem(
    val id: String,
    val title: String,
    val feedName: String,
    val publishedAt: String,
)
