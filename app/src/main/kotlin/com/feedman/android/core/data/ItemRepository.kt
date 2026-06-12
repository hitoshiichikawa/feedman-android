package com.feedman.android.core.data

import com.feedman.android.core.model.MockTimelineItem
import kotlinx.coroutines.flow.Flow

/**
 * Boundary for reading the cross-feed timeline (Req 3.2, 3.3, 4.4).
 *
 * The skeleton binds this interface to [com.feedman.android.core.data.fake.FakeItemRepository]
 * via Hilt so that feature modules can depend on the abstraction only. The full
 * Retrofit-backed implementation will replace the Fake in a subsequent Issue without
 * touching call sites.
 *
 * Note: this skeleton uses [MockTimelineItem] (mock-only type, Issue #1). The SPEC §4.2
 * API model [com.feedman.android.core.model.ItemSummary] is introduced separately by
 * Issue #15; the integration / replacement policy is deferred to a follow-up Issue.
 */
interface ItemRepository {
    /**
     * Returns a hot/cold [Flow] of the current timeline snapshot.
     *
     * - Postcondition: emits at least one snapshot for each subscriber.
     * - Invariant: multiple subscribers may collect concurrently.
     */
    fun observeTimeline(): Flow<List<MockTimelineItem>>
}
