package com.feedman.android.core.data

import com.feedman.android.core.model.Subscription
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.network.SubscriptionSettingsRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
) : SubscriptionRepository, UserScopedCache {

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
     * Issue #41 Req 3.5 / 3.7 / 3.8: 停止 / エラー状態の購読を再開する。
     *
     * `POST /api/subscriptions/{id}/resume`（SPEC §4.2）を呼び出し、成功時は返ってきた
     * Subscription を内部の `_subscriptions` の該当 entry を置換して反映する。これにより
     * [observeSubscriptions] / [observeFeed] を購読中の UI（ドロワー / FeedScreen）が新しい
     * 状態（active）を観測する。
     *
     * 失敗時は [FeedmanException] をそのまま呼び出し元へ投げ返す（UI 側で snackbar 表示する
     * ため。Req 3.8）。本メソッドは [refreshMutex] でガードしないため、refresh と並行に
     * 呼ばれても安全（_subscriptions の `update` は MutableStateFlow の atomic 操作）。
     *
     * @param subscriptionId 対象 [Subscription.id]
     * @return 再開後の最新 Subscription
     */
    override suspend fun resume(subscriptionId: String): Subscription {
        val updated = api.resumeSubscription(subscriptionId)
        _subscriptions.update { current ->
            current.map { existing ->
                // SPEC §4.2 の id は ULID で一意。一致したエントリのみ置換する。
                if (existing.id == subscriptionId) updated else existing
            }
        }
        return updated
    }

    /**
     * Issue #43 Req 2.4 / 5.2: 購読設定（フェッチ間隔）を更新する。
     *
     * `PUT /api/subscriptions/{id}/settings` を呼び出し、成功時は返ってきた Subscription を
     * 内部 `_subscriptions` の該当 entry に置換して反映する（observeSubscriptions /
     * observeFeed 経由でドロワー / 設定シート両方に新値が流れる）。
     *
     * 失敗時は [FeedmanException] を呼び出し元へ透過する（UI 側で旧値ロールバック）。
     * 本メソッドは [refreshMutex] でガードしない。
     */
    override suspend fun updateSettings(
        subscriptionId: String,
        fetchIntervalMinutes: Int,
    ): Subscription {
        val updated = api.updateSubscriptionSettings(
            subscriptionId = subscriptionId,
            request = SubscriptionSettingsRequest(fetchIntervalMinutes = fetchIntervalMinutes),
        )
        _subscriptions.update { current ->
            current.map { existing ->
                if (existing.id == subscriptionId) updated else existing
            }
        }
        return updated
    }

    /**
     * Issue #43 Req 4.3 / 4.4: 購読解除を実行する。
     *
     * `DELETE /api/subscriptions/{id}` を呼び出し、成功時は内部 `_subscriptions` から該当
     * entry を除去する。observeSubscriptions Flow が新しい（当該フィードを含まない）リストを
     * 流すため、ドロワーの購読一覧は自動的に当該フィードを表示しなくなる（Req 4.4）。
     *
     * 失敗時は [FeedmanException] を呼び出し元へ透過する（UI 側でリスト・画面遷移を
     * 変更せずエラー表示。Req 4.7）。
     */
    override suspend fun unsubscribe(subscriptionId: String) {
        api.deleteSubscription(subscriptionId)
        _subscriptions.update { current ->
            current.filterNot { it.id == subscriptionId }
        }
    }

    /**
     * Issue #42 Req 1.1 / 2.1 / 2.3 / 3.x / 4.x: 手動フェッチを実行する。
     *
     * `POST /api/subscriptions/{id}/fetch`（SPEC §4.2）を呼び出し、成功時は返ってきた
     * Subscription を内部の `_subscriptions` の該当 entry へ置換して反映する。これにより
     * [observeSubscriptions] を購読中のドロワーが新しい unread_count バッジを観測する
     * （Req 2.3）。
     *
     * クールダウン応答（HTTP 429 + `code = FEED_COOLDOWN`）や、その他の非 2xx 応答・
     * I/O 失敗は network 層で [com.feedman.android.core.network.FeedmanException] に変換
     * 済みのため、本実装は変換を行わず例外を呼び出し元へ透過する（Req 3.1〜3.3 / 4.1〜4.3）。
     * 本メソッドは [refreshMutex] でガードしないため、refresh と並行に呼ばれても安全。
     *
     * @param subscriptionId 対象 [Subscription.id]
     * @return 手動フェッチ後の最新 Subscription
     */
    override suspend fun fetch(subscriptionId: String): Subscription {
        val updated = api.fetchSubscription(subscriptionId)
        _subscriptions.update { current ->
            current.map { existing ->
                if (existing.id == subscriptionId) updated else existing
            }
        }
        return updated
    }

    /**
     * ログアウト時に購読リストと取得状態を初期状態に戻す（Issue #50 Req 3.1）。
     *
     * - [_subscriptions] を空リストに戻す（前ユーザーのフィード一覧を破棄）
     * - [_loadState] を [SubscriptionLoadState.Idle] に戻す（前ユーザーのエラー / Success 状態を破棄）
     *
     * 並行な [refresh] 進行中は [refreshMutex] によりリセット完了後に新しい値が確定する。
     * 本メソッドは I/O を伴わず、StateFlow への書き込みのみで完結する。
     */
    override suspend fun reset() {
        _subscriptions.value = emptyList()
        _loadState.value = SubscriptionLoadState.Idle
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
