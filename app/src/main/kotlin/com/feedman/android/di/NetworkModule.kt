package com.feedman.android.di

import com.feedman.android.core.auth.AuthInterceptor
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
 * - Issue #21 で [AuthInterceptor] を `additionalInterceptors` に注入し、TokenStore に保存された
 *   access token を `Authorization: Bearer <token>` として全 API リクエストへ付与する。
 *   認証不要エンドポイント（`/api/auth/token` / `/api/auth/refresh`）の除外は
 *   [AuthInterceptor] 内部で判定する（Req 4.3）。
 * - 401 自動 refresh + リトライを担う TokenAuthenticator は後続 Issue #22 で本モジュールへ
 *   `authenticator` として注入する想定。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideFeedmanApi(
        appConfig: AppConfig,
        authInterceptor: AuthInterceptor,
    ): FeedmanApi {
        return ApiClientFactory.create(
            baseUrl = appConfig.baseUrl,
            additionalInterceptors = listOf(authInterceptor),
            authenticator = null,
        )
    }
}
