package com.feedman.android.di

import com.feedman.android.core.model.AppConfig
import com.feedman.android.core.network.ApiClientFactory
import com.feedman.android.core.network.FeedmanApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring [FeedmanApi] from [AppConfig.baseUrl]（Issue #17 Req 2.1 / NFR 2.2）。
 *
 * - [AppConfigModule] が `BuildConfig.BASE_URL` を [AppConfig] として提供しているため、
 *   network 層は [AppConfig] を経由して BASE_URL を取得する（コードに固定 URL を
 *   埋め込まない）。
 * - 追加 interceptor / authenticator は本モジュールでは注入していない。後続 Issue
 *   #21（AuthInterceptor）/ #22（TokenAuthenticator）が、本モジュールを
 *   `@Provides` の差し替え or 別モジュールでの上書きにより wiring する想定（Req 4.1）。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideFeedmanApi(appConfig: AppConfig): FeedmanApi {
        return ApiClientFactory.create(
            baseUrl = appConfig.baseUrl,
            additionalInterceptors = emptyList(),
            authenticator = null,
        )
    }
}
