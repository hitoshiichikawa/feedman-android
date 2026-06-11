package com.feedman.android.di

import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.data.fake.FakeItemRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding the skeleton's Fake repository implementations as the default
 * production graph (Req 3.1, 3.3, 3.4).
 *
 * Real implementations will replace [FakeItemRepository] in subsequent Issues by
 * swapping a single `@Binds` line; call sites remain unchanged.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: FakeItemRepository): ItemRepository
}
