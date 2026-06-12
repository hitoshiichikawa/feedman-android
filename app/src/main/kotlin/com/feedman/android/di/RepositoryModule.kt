package com.feedman.android.di

import com.feedman.android.core.data.CrossFeedRepository
import com.feedman.android.core.data.CrossFeedRepositoryImpl
import com.feedman.android.core.data.FeedItemsRepository
import com.feedman.android.core.data.FeedItemsRepositoryImpl
import com.feedman.android.core.data.FeedRegistrationRepository
import com.feedman.android.core.data.FeedRegistrationRepositoryImpl
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemDetailRepositoryImpl
import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.data.StarredItemsRepository
import com.feedman.android.core.data.StarredItemsRepositoryImpl
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.data.SubscriptionRepositoryImpl
import com.feedman.android.core.data.fake.FakeItemRepository
import com.feedman.android.core.data.fake.FakeSubscriptionRepository
import com.feedman.android.core.model.AppConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
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
     * フィード別記事一覧の実 API 用リポジトリ（Issue #40 Req 1〜4 / NFR 1 / 2）。
     *
     * `GET /api/feeds/{id}/items?filter=all|unread|starred` を呼ぶ Pager データ層として独立に
     * 注入される。フィード別画面 UI（#41）はこの interface のみに依存し、フィルタごとの
     * `Flow<PagingData<ItemSummary>>` を購読する。
     */
    @Binds
    @Singleton
    abstract fun bindFeedItemsRepository(impl: FeedItemsRepositoryImpl): FeedItemsRepository

    /**
     * スター一覧の実 API 用リポジトリ（Issue #46 Req 2 / 3 / NFR 2.3）。
     *
     * `GET /api/feeds/starred/items` を Pager データ層として独立に注入する。スター一覧画面
     * （feature/starred）はこの interface のみに依存し、`Flow<PagingData<StarredItemSummary>>`
     * を購読する。
     */
    @Binds
    @Singleton
    abstract fun bindStarredItemsRepository(impl: StarredItemsRepositoryImpl): StarredItemsRepository

    /**
     * 記事詳細・状態更新リポジトリ（Issue #35 Req 1 / 2 / 3）。
     *
     * 記事詳細シート（#36）と楽観的更新同期（#38）が共有するデータ層。実 API 用の単一実装
     * のみ提供し、Fake 系は不要（v1 スコープ外）。
     */
    @Binds
    @Singleton
    abstract fun bindItemDetailRepository(impl: ItemDetailRepositoryImpl): ItemDetailRepository

    /**
     * フィード登録リポジトリ（Issue #44 / Req 3.1 / Req 5.x）。
     *
     * `POST /api/feeds`（SPEC §4.2）の薄い委譲層。Fake 実装は v1 スコープでは不要のため、
     * 単一実装で固定（mockMode 切替の対象外）。
     */
    @Binds
    @Singleton
    abstract fun bindFeedRegistrationRepository(
        impl: FeedRegistrationRepositoryImpl,
    ): FeedRegistrationRepository

    companion object {

        /**
         * 購読リポジトリの解決（Issue #30 + Issue #39 / Req 3.1, 3.2, 3.3, 3.4 / NFR 2.3）。
         *
         * `AppConfig.mockMode` に応じて Fake / 実 API 実装を選択する。切替判断は純粋関数
         * [selectSubscriptionRepository] に切り出し、本モジュールは「どの依存をフレームワーク
         * から取り寄せるか」だけを担う（NFR 2.3: 単一箇所での差し替え）。
         *
         * - `mockMode = true`  → `FakeSubscriptionRepository`（Req 3.1, 3.2: API を呼ばない）
         * - `mockMode = false` → `SubscriptionRepositoryImpl`（Req 3.3: 実 API 経路）
         */
        @Provides
        @Singleton
        fun provideSubscriptionRepository(
            appConfig: AppConfig,
            fake: FakeSubscriptionRepository,
            real: SubscriptionRepositoryImpl,
        ): SubscriptionRepository = selectSubscriptionRepository(
            mockMode = appConfig.mockMode,
            fake = fake,
            real = real,
        )
    }
}
