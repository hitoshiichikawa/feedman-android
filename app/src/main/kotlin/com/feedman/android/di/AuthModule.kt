package com.feedman.android.di

import com.feedman.android.core.auth.EncryptedPrefsTokenStore
import com.feedman.android.core.auth.MockModeSessionStateProvider
import com.feedman.android.core.auth.SessionStateProvider
import com.feedman.android.core.auth.TokenStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring [TokenStore] to its production binding
 * （Issue #20 / Req 4.1, 4.2）。
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
     * [SessionStateProvider] の本番バインディング（Issue #29 / Req 3.5）。
     *
     * 本 Issue 時点では `MockModeSessionStateProvider` を採用し、`AppConfig.mockMode` に
     * 応じて `LoggedIn` / `LoggedOut` を返す。Issue #24 系で本格的な実装に置き換える際は
     * 本 `@Binds` の 1 行差し替えで済む。テスト時は `@TestInstallIn` か `@BindValue` で
     * 任意の [SessionStateProvider] 実装を差し込めるよう、抽象 binding として宣言する。
     */
    @Binds
    @Singleton
    abstract fun bindSessionStateProvider(
        impl: MockModeSessionStateProvider,
    ): SessionStateProvider
}
