package com.feedman.android.di

import com.feedman.android.core.auth.AuthRepository
import com.feedman.android.core.auth.AuthRepositoryImpl
import com.feedman.android.core.auth.AuthRepositorySessionStateProvider
import com.feedman.android.core.auth.EncryptedPrefsTokenStore
import com.feedman.android.core.auth.MockModeSessionStateProvider
import com.feedman.android.core.auth.SessionStateProvider
import com.feedman.android.core.auth.TokenStore
import com.feedman.android.core.model.AppConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt module wiring [TokenStore] / [AuthRepository] to their production bindings
 * （Issue #20 / #21 / Req 4.1, 4.2）。
 *
 * 本モジュールでは `EncryptedSharedPreferences` 裏付けの [EncryptedPrefsTokenStore] を
 * デフォルトで提供する。テストや mockMode でテスト用 fake
 * （`com.feedman.android.core.auth.fake.InMemoryTokenStore`）に差し替える場合は、Hilt の
 * `@TestInstallIn` または `@BindValue` を用いた本モジュールの置換で対応する（Req 4.3）。
 * 本番ソースを変更せずに差し替えできる構成のため、Req 4.3 の "without modifying production
 * source files" 要件を満たす。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: EncryptedPrefsTokenStore): TokenStore

    /**
     * [AuthRepository] の本実装バインディング（Issue #21）。
     */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {
        /**
         * AuthRepositoryImpl が TokenSet の expires_at 計算に使う [Clock] を提供する。
         * テスト時は `@TestInstallIn` で固定 [Clock] に差し替える。
         */
        @Provides
        @Singleton
        fun provideAuthClock(): Clock = Clock.systemUTC()

        /**
         * [SessionStateProvider] の本番バインディング（Issue #29 / #23 Req 3.3）。
         *
         * - `AppConfig.mockMode = true`: [MockModeSessionStateProvider] を採用し、
         *   `mockMode` 連動の暫定ログイン状態（常時 LoggedIn）で動作する（既存挙動）。
         * - `AppConfig.mockMode = false`: [AuthRepositorySessionStateProvider] を採用し、
         *   `AuthRepository.observeIsAuthenticated()` の値を [com.feedman.android.core.auth.SessionState]
         *   にマップする。Issue #23 の Login Flow が `AuthRepository.exchange` を呼んで
         *   成功すると、AppShell が自動的に LoggedIn に切替わる（Req 3.3）。
         *
         * `Provider` 経由で必要な実装だけ実体化することで、mockMode = true 環境では
         * `AuthRepositorySessionStateProvider` を一切初期化しない（逆も同様）。
         */
        @Provides
        @Singleton
        fun provideSessionStateProvider(
            appConfig: AppConfig,
            mockModeProvider: Provider<MockModeSessionStateProvider>,
            authRepositoryProvider: Provider<AuthRepositorySessionStateProvider>,
        ): SessionStateProvider = if (appConfig.mockMode) {
            mockModeProvider.get()
        } else {
            authRepositoryProvider.get()
        }
    }
}
