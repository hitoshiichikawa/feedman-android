package com.feedman.android.di

import com.feedman.android.core.auth.PkceGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import javax.inject.Singleton

/**
 * Hilt binding for [PkceGenerator] used by [com.feedman.android.feature.login.LoginViewModel]
 * (Issue #23 / Req 2.2).
 *
 * 本番では [SecureRandom] backed の default 実装を提供する。テストで決定論的な PKCE pair を
 * 生成したい場合は `@TestInstallIn` で本モジュールを置換するか、ViewModel テストのように
 * fake [PkceGenerator] をコンストラクタに直接注入する（CLAUDE.md テスト規約に従い、
 * Hilt 自体はテストランナーで使わず直接 ViewModel をインスタンス化する）。
 */
@Module
@InstallIn(SingletonComponent::class)
object PkceModule {

    @Provides
    @Singleton
    fun providePkceGenerator(): PkceGenerator = PkceGenerator.default(SecureRandom())
}
