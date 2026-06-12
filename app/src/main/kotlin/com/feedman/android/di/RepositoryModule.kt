package com.feedman.android.di

import com.feedman.android.core.data.CrossFeedRepository
import com.feedman.android.core.data.CrossFeedRepositoryImpl
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemDetailRepositoryImpl
import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.data.fake.FakeItemRepository
import com.feedman.android.core.data.fake.FakeSubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding the skeleton's Fake repository implementations as the default
 * production graph (Req 3.1, 3.3, 3.4).
 *
 * Real implementations will replace [FakeItemRepository] / [FakeSubscriptionRepository]
 * in subsequent Issues by swapping a single `@Binds` line; call sites remain unchanged.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: FakeItemRepository): ItemRepository

    /**
     * 購読フィードのリポジトリを Fake にバインドする（Issue #30 / Req 5.1, 5.2 / NFR 3.1）。
     * #39 で実 API 実装に差し替える際は本行 1 つを書き換えれば足りる。
     */
    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: FakeSubscriptionRepository): SubscriptionRepository

    /**
     * 横断新着タイムラインの実 API 用リポジトリ（Issue #32 Req 1〜5 / NFR 2.2）。
     *
     * [bindItemRepository] のモック実装（[FakeItemRepository]）とは別系統で、Pager 用の
     * cross-feed データ層として独立に注入される。タイムライン UI（#33）はこの interface
     * のみに依存し、`Flow<PagingData<CrossFeedItem>>` を購読する。
     */
    @Binds
    @Singleton
    abstract fun bindCrossFeedRepository(impl: CrossFeedRepositoryImpl): CrossFeedRepository

    /**
     * 記事詳細・状態更新リポジトリ（Issue #35 Req 1 / 2 / 3）。
     *
     * 記事詳細シート（#36）と楽観的更新同期（#38）が共有するデータ層。実 API 用の単一実装
     * のみ提供し、Fake 系は不要（v1 スコープ外）。
     */
    @Binds
    @Singleton
    abstract fun bindItemDetailRepository(impl: ItemDetailRepositoryImpl): ItemDetailRepository
}
