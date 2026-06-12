package com.feedman.android.di

import com.feedman.android.BuildConfig
import com.feedman.android.core.model.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides an [AppConfig] value object sourced from generated [BuildConfig] fields
 * (Req 5.1, 5.2).
 *
 * Reading `BuildConfig` here (and only here) keeps the rest of the codebase testable:
 * ViewModels and Composables depend on [AppConfig] instead of the generated class.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig(
        baseUrl = BuildConfig.BASE_URL,
        mockMode = BuildConfig.MOCK_MODE,
    )
}
