package com.feedman.android.core.data.fake

import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.model.ItemSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory Fake implementation of [ItemRepository] used by the mock mode and tests
 * (Req 3.2, 3.3, 4.4).
 *
 * Returns a fixed snapshot of mock articles. The implementation is intentionally
 * side-effect free so that unit tests can rely on deterministic output.
 */
@Singleton
class FakeItemRepository @Inject constructor() : ItemRepository {

    override fun observeTimeline(): Flow<List<ItemSummary>> = flowOf(MOCK_ITEMS)

    companion object {
        internal val MOCK_ITEMS: List<ItemSummary> = listOf(
            ItemSummary(
                id = "mock-1",
                title = "Feedman Android スケルトンを作成しました",
                feedName = "Feedman Dev Blog",
                publishedAt = "10 分前",
            ),
            ItemSummary(
                id = "mock-2",
                title = "Jetpack Compose Material 3 の最新動向",
                feedName = "Android Developers",
                publishedAt = "1 時間前",
            ),
            ItemSummary(
                id = "mock-3",
                title = "Kotlin Coroutines 1.9 のリリースノート",
                feedName = "Kotlin Blog",
                publishedAt = "3 時間前",
            ),
        )
    }
}
