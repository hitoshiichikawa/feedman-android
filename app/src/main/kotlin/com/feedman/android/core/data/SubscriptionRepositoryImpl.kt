package com.feedman.android.core.data

import com.feedman.android.core.model.Subscription
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SubscriptionRepository] の本実装（Issue #39 / Req 1, 2, 4 / NFR 2.1）。
 *
 * [FeedmanApi.getSubscriptions] へ薄く委譲し、`GET /api/subscriptions` の応答を
 * [SubscriptionLoadState] と購読フィードリストの 2 つの `StateFlow` として公開する。
 *
 * ## 状態管理
 *
 * - [_subscriptions]: 最後に成功した購読フィードリスト。初期値は空リスト。取得失敗時は
 *   **過去の成功値を維持** する（UI 側でエラー表示と並行して直近リストを引き続き読める
 *   構造）。サーバーが空配列を返したら空に置換する（Req 1.5）。
 * - [_loadState]: 取得状態。初期値は [SubscriptionLoadState.Idle]。`refresh()` 起動で
 *   Loading に遷移し、結果に応じて Success / Error に更新する（Req 2.1, 2.5, 2.6, 4.3）。
 *
 * ## エラー透過
 *
 * `FeedmanApi` は OkHttp の `FeedmanErrorMappingInterceptor` + `FeedmanApiProxy` で
 * 非 2xx・I/O 失敗をすべて [FeedmanException] に変換済みのため、本実装側で例外型を分岐
 * せずに `try/catch (FeedmanException)` で受ければ十分。サーバーが SPEC §4.3 統一エラーを
 * 返した場合の `code` / `message` はそのまま [SubscriptionLoadState.Error] に転写する
 * （Req 2.6）。401 については ApiClientFactory に注入される共通 Authenticator が再認証を
 * 試みた **後** の最終結果が本実装に届く（Req 4.1, 4.2）。再認証後も 401 が継続した場合は
 * `code = "UNAUTHORIZED"` 等のサーバー由来 code を持つ Error として観測者へ通知される
 * （Req 4.3）。
 *
 * ## 並行制御
 *
 * [refresh] は [refreshMutex] で in-flight 1 件に直列化される。連打されても 2 回目の API
 * 呼び出しが終わってから 3 回目が走る。
 *
 * ## DI
 *
 * [com.feedman.android.di.RepositoryModule] が `AppConfig.mockMode = false` のとき
 * `@Provides` で本実装を [SubscriptionRepository] にバインドする。
 */
@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
) : SubscriptionRepository {

    private val _subscriptions: MutableStateFlow<List<Subscription>> = MutableStateFlow(emptyList())
    private val _loadState: MutableStateFlow<SubscriptionLoadState> =
        MutableStateFlow(SubscriptionLoadState.Idle)

    /** 並行 refresh の直列化用ミューテックス。 */
    private val refreshMutex: Mutex = Mutex()

    /** 公開用 read-only ビュー（[asStateFlow]）。テストで初期値や遷移を観測できる。 */
    val subscriptionsState: StateFlow<List<Subscription>> get() = _subscriptions.asStateFlow()

    /** 公開用 read-only ビュー。 */
    val loadState: StateFlow<SubscriptionLoadState> get() = _loadState.asStateFlow()

    override fun observeSubscriptions(): Flow<List<Subscription>> = _subscriptions.asStateFlow()

    override fun observeLoadState(): Flow<SubscriptionLoadState> = _loadState.asStateFlow()

    /**
     * `GET /api/subscriptions` を実行して状態を更新する（Req 2.4 / 2.5 / 4.x）。
     *
     * 例外は本メソッド内で完結し、呼び出し元へは投げ返さない（UI 側の try/catch を
     * 不要にするための契約）。FeedmanException 以外の予期しない例外（バグ起因）は
     * `UNKNOWN_ERROR` の Error 状態に包んで通知する。
     */
    override suspend fun refresh() {
        refreshMutex.withLock {
            _loadState.value = SubscriptionLoadState.Loading
            val result = runCatching { api.getSubscriptions() }
            result.fold(
                onSuccess = { fetched ->
                    // Req 1.4: サーバーが返した順序のまま反映する（並び替えしない）
                    _subscriptions.value = fetched
                    _loadState.value = SubscriptionLoadState.Success
                },
                onFailure = { throwable ->
                    _loadState.value = toErrorState(throwable)
                },
            )
        }
    }

    /**
     * 例外を [SubscriptionLoadState.Error] に変換する（Req 2.1, 2.6, 4.3）。
     *
     * [FeedmanException] の場合は code / message をそのまま転写し、それ以外の予期しない
     * 例外は `UNKNOWN_ERROR` フォールバックメッセージで包む（防衛的）。
     */
    private fun toErrorState(throwable: Throwable): SubscriptionLoadState.Error {
        return when (throwable) {
            is FeedmanException -> SubscriptionLoadState.Error(
                message = throwable.errorMessage.ifBlank {
                    FeedmanException.FALLBACK_UNKNOWN_MESSAGE
                },
                code = throwable.code,
            )
            else -> SubscriptionLoadState.Error(
                message = FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
                code = FeedmanException.CODE_UNKNOWN_ERROR,
            )
        }
    }
}
