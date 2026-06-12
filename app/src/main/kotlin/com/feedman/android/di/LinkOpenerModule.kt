package com.feedman.android.di

import com.feedman.android.core.ui.CustomTabsLinkOpener
import com.feedman.android.core.ui.LinkOpener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Chrome Custom Tabs 経由のリンクオープナーを Hilt にバインドする（Issue #37）。
 *
 * [LinkOpener] は副作用のみを持つ stateless な singleton として `@Provides` で生成する
 * （`@Binds` ではなく `@Provides` を用いるのは、[CustomTabsLinkOpener] が `@Inject`
 * コンストラクタを持たないため）。実装差し替え（将来の「完全外部ブラウザ切替設定」など）
 * の際は本 module の return 文 1 つを書き換えれば足りる。
 */
@Module
@InstallIn(SingletonComponent::class)
object LinkOpenerModule {

    @Provides
    @Singleton
    fun provideLinkOpener(): LinkOpener = CustomTabsLinkOpener()
}
