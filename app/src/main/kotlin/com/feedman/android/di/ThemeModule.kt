package com.feedman.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.feedman.android.core.designsystem.DataStoreThemeModeRepository
import com.feedman.android.core.designsystem.ThemeModeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing [ThemeModeRepository] とその裏付け [DataStore]（Issue #25 / Req 3）。
 *
 * - [DataStore] は `PreferenceDataStoreFactory` でアプリ専用ディレクトリ
 *   （`<files>/datastore/feedman_theme.preferences_pb`）へ書き込む。
 * - [ThemeModeRepository] は `DataStoreThemeModeRepository` を本番バインディングとして公開する。
 * - テスト / モックモード時は `@TestInstallIn` または `@BindValue` で
 *   `InMemoryThemeModeRepository` に差し替え可能。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeModule {

    @Binds
    @Singleton
    abstract fun bindThemeModeRepository(
        impl: DataStoreThemeModeRepository,
    ): ThemeModeRepository

    companion object {
        /** Preferences DataStore ファイル名。スキーマ互換性のため不用意に変えないこと。 */
        private const val THEME_DATASTORE_NAME = "feedman_theme"

        @Provides
        @Singleton
        @JvmStatic
        fun provideThemeDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(THEME_DATASTORE_NAME) },
        )
    }
}
