package com.feedman.android.di

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
}
