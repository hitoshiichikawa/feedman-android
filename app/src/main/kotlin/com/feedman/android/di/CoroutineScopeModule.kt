package com.feedman.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * アプリケーション全体で共有する長寿命 [CoroutineScope] の修飾子（Issue #38）。
 *
 * `viewModelScope` よりも寿命が長く、画面遷移を超えて生存する必要のあるシングルトン
 * （[com.feedman.android.core.data.ItemStateStore] など）が、サーバー反映処理を発行する
 * ためにこの scope を注入する。`SupervisorJob` を用いることで、1 つの子コルーチンの
 * 失敗が他の処理を巻き込まないようにする。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Hilt module providing the application-wide [CoroutineScope] (Issue #38).
 *
 * `SupervisorJob() + Dispatchers.Default` を採用する:
 * - `SupervisorJob`: 子コルーチンの個別失敗を他に伝播させない
 * - `Dispatchers.Default`: I/O は Retrofit / OkHttp が独自スレッドで吸収するため
 *   ここでは CPU バウンド既定で十分（楽観的更新の overlay 反映ロジック自体は軽量）
 *
 * テストでは Hilt を通さず、コンストラクタへ別 scope を直接渡して差し替える
 * （[com.feedman.android.core.data.ItemStateStoreTest] 参照）。
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
