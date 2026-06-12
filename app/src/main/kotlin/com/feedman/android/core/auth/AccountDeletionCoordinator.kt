package com.feedman.android.core.auth

import com.feedman.android.core.data.CrossFeedRepositoryImpl
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.data.SubscriptionRepositoryImpl
import com.feedman.android.core.data.UserRepository
import com.feedman.android.core.data.UserScopedCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AccountDeletionCoordinator] の最小契約（Issue #51）。
 *
 * ViewModel から DI 経由で参照する境界を本インターフェースで切り出し、テスト時に
 * 軽量な fake で差し替えられるようにする。
 *
 * ## 結果モデル
 *
 * - 成功時: [DeletionResult.Success] を返す。**呼び出し直前にローカルクレデンシャル消去 +
 *   ユーザースコープキャッシュリセットが完了済み**（Req 4.1 / 4.2）。
 *   SessionState 遷移は本クラスでは直接行わず、[AuthRepository.observeIsAuthenticated] が
 *   `false` に遷移する経路を経由して [SessionState.LoggedOut] へ遷移する（Req 4.3 / 4.4）。
 * - 失敗時: [DeletionResult.Failure] を返す。**ローカルクレデンシャル / キャッシュ状態は
 *   一切変更されない**（Req 5.1 / 5.2 / 5.3）。`message` を UI が表示する（Req 5.4）。
 */
interface AccountDeletionCoordinator {
    /**
     * 退会処理（`DELETE /api/users/me` → ローカル消去 + キャッシュリセット）を 1 回実行する。
     *
     * 本メソッドは例外を投げない契約。失敗は [DeletionResult.Failure] として表現される。
     */
    suspend fun perform(): DeletionResult
}

/**
 * [AccountDeletionCoordinator.perform] の結果型（Issue #51 Req 4 / 5）。
 */
sealed interface DeletionResult {
    /**
     * Req 4.1〜4.5: 退会成功。本値が返された時点で:
     * - TokenStore は空（access token / refresh token が消去済み）
     * - ユーザースコープキャッシュは初期化済み
     * - SessionState 遷移は [AuthRepository.observeIsAuthenticated] 経由で発火する
     */
    data object Success : DeletionResult

    /**
     * Req 5.1〜5.5: 退会失敗。本値が返された時点で:
     * - TokenStore は維持されたまま（access token / refresh token が温存）
     * - ユーザースコープキャッシュは維持されたまま
     * - SessionState は LoggedIn のまま
     *
     * @property message ユーザーに表示する失敗文言（FeedmanException.errorMessage を優先、
     *   ネットワーク失敗 / unknown は汎用フォールバック文言）。空文字にはならない。
     */
    data class Failure(val message: String) : DeletionResult
}

/**
 * 退会処理の調整役（Issue #51 Req 4 / 5 / NFR 2 / NFR 3）。
 *
 * `AccountSheetViewModel` の「退会を実行する」確定操作起点で呼び出され、以下を 1 トランザクション
 * として実行する:
 *
 * 1. [UserRepository.deleteMe] を 1 回呼び出す（Req 2.6）
 *    - 成功 → 次フェーズへ進む（Req 4.1〜4.5）
 *    - [FeedmanException][com.feedman.android.core.network.FeedmanException] →
 *      ローカル消去・キャッシュリセットを **一切行わず** [DeletionResult.Failure] を返す
 *      （Req 5.1 / 5.2 / 5.3 / 5.4）。
 *    - 想定外例外 → 同上（Failure として表現、ローカル状態は無変更）
 * 2. TokenStore を消去する（Req 4.1）
 *    - revoke は呼ばない。**サーバー側でアカウントが既に消えているため revoke は不要**
 *      （SPEC §5.7 の DELETE /api/users/me がアカウント本体を削除する設計）。
 *    - revoke を呼ばないことでネットワーク失敗による不要な遅延を避ける（NFR 1.1 の体感）。
 * 3. ユーザースコープの in-memory キャッシュ（[UserScopedCache] 実装）を順に [UserScopedCache.reset]
 *    で初期化する（Req 4.2）
 *    - 明示列挙方式（multibinding を使わない）でリセット漏れを発見しやすくする
 *    - 各 reset が例外を投げても他の reset は続行する（NFR 2.2: クレデンシャル消去の確実性）
 * 4. SessionState 遷移は本クラスでは直接行わず、`TokenStore.clear()` → `observeIsAuthenticated`
 *    が false に遷移する経路に委ねる（Req 4.3 / 4.4）
 *
 * ## ログ / 個人情報の扱い（NFR 2.1 / NFR 3.1）
 *
 * - 本クラスは access token / refresh token / email など個人識別情報を引数で受け取らず、
 *   ログ出力も行わない。FeedmanException の errorMessage は **ユーザー表示** のために
 *   [DeletionResult.Failure.message] に乗せるだけで、クラッシュレポート本文等への出力は
 *   呼び出し側の責務とする（現状の実装は明示的なログを出さない）。
 *
 * ## LogoutCoordinator との関係
 *
 * 一見 [LogoutCoordinator] と重複するが、以下の点で挙動が異なるため独立クラスとした:
 *
 * - revoke を呼ばない（アカウント自体が消えるため不要）
 * - 失敗時にローカル状態を **温存** する（Req 5.1〜5.3）。LogoutCoordinator は失敗時も
 *   best-effort でローカル消去する設計（ログアウト意図の確定）であり、判断が逆。
 *
 * @param userRepository deleteMe (DELETE /api/users/me) を担うデータ境界
 * @param tokenStore 端末上のアクセストークン / リフレッシュトークン保管庫
 * @param itemStateStore / @param subscriptionRepository / @param crossFeedRepository
 *   リセット対象のユーザースコープキャッシュ群（[LogoutCoordinatorImpl] と同じ列挙）
 */
@Singleton
class AccountDeletionCoordinatorImpl @Inject constructor(
    private val userRepository: UserRepository,
    private val tokenStore: TokenStore,
    // AuthRepositoryImpl の `refreshAuthenticatedState` を呼び出す必要があるため、
    // 抽象 [AuthRepository] ではなく実装型 [AuthRepositoryImpl] を直接受け取る。
    // テストでも実物を使用する設計（CLAUDE.md テスト規約: 自分が書いたロジックはモックしない）。
    private val authRepository: AuthRepositoryImpl,
    // 明示列挙: 個別の Singleton 実装を直接受け取り、リセット漏れを発見しやすくする。
    itemStateStore: ItemStateStore,
    subscriptionRepository: SubscriptionRepository,
    crossFeedRepository: CrossFeedRepositoryImpl,
) : AccountDeletionCoordinator {

    /**
     * リセット対象のユーザースコープキャッシュ群（[LogoutCoordinatorImpl] と同じ列挙）。
     */
    private val userScopedCaches: List<UserScopedCache> = buildList {
        add(itemStateStore)
        if (subscriptionRepository is SubscriptionRepositoryImpl) {
            add(subscriptionRepository)
        }
        add(crossFeedRepository)
    }

    /**
     * 退会処理を実行する（Issue #51 Req 4 / 5 / NFR 2 / NFR 3）。
     *
     * - 例外を投げない契約。失敗は [DeletionResult.Failure] として返す。
     * - 失敗時の TokenStore / キャッシュ温存（Req 5.1 / 5.2 / 5.3）は、失敗パスで
     *   `tokenStore.clear()` / `cache.reset()` を一切呼ばないことで保証する。
     */
    override suspend fun perform(): DeletionResult {
        // Phase 1: DELETE /api/users/me（Req 2.6）
        try {
            userRepository.deleteMe()
        } catch (e: com.feedman.android.core.network.FeedmanException) {
            // Req 5.1 / 5.2 / 5.3 / 5.4 / 5.5: 失敗時はローカル状態を温存し Failure を返す
            return DeletionResult.Failure(resolveFailureMessage(e))
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Coroutines キャンセルは再 throw（runTest 等の cooperative cancel を妨げない）
            throw e
        } catch (e: Exception) {
            // 想定外例外: ローカル状態は変更しない（safety first / Req 5.1〜5.3）
            return DeletionResult.Failure(
                e.message?.takeIf { it.isNotBlank() }
                    ?: com.feedman.android.core.network.FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
            )
        }

        // Phase 2: TokenStore 消去（Req 4.1）。
        // revoke は呼ばない（アカウント自体が消えているため不要）。
        runCatching { tokenStore.clear() }
        // observeIsAuthenticated の StateFlow を最新の TokenStore 状態（空）と同期する
        // ことで、AuthRepositorySessionStateProvider が LoggedOut を流す経路に乗せる
        // （Req 4.3 / 4.4）。本呼び出しはサーバー通信を伴わない（read + StateFlow 更新のみ）。
        runCatching { authRepository.refreshAuthenticatedState() }

        // Phase 3: ユーザースコープキャッシュのリセット（Req 4.2 / NFR 2.2）。
        // 個別の reset 失敗が他に伝播しないように runCatching で守る。
        for (cache in userScopedCaches) {
            runCatching { cache.reset() }
        }

        // Phase 4: SessionState 遷移は本クラスでは行わない（Req 4.3 / 4.4）。
        // TokenStore が空になったことで observeIsAuthenticated が false に遷移し、
        // AuthRepositorySessionStateProvider が LoggedOut を流す → AppShell が LoginScreen を描画。
        return DeletionResult.Success
    }

    /**
     * 失敗文言を解決する（Issue #51 Req 5.4 / impl-notes の判断記録）。
     *
     * - FeedmanException.errorMessage が非空 → そのまま採用（サーバーが返した文言を優先）
     * - errorMessage が空文字 → code 別の汎用フォールバック文言を採用
     *   - NETWORK_ERROR: ネットワーク汎用文言（Req 5.3）
     *   - その他: 不明エラー汎用文言（Req 5.1 / 5.4）
     */
    private fun resolveFailureMessage(
        e: com.feedman.android.core.network.FeedmanException,
    ): String = e.errorMessage.ifBlank {
        when (e.code) {
            com.feedman.android.core.network.FeedmanException.CODE_NETWORK_ERROR ->
                com.feedman.android.core.network.FeedmanException.FALLBACK_NETWORK_MESSAGE
            else ->
                com.feedman.android.core.network.FeedmanException.FALLBACK_UNKNOWN_MESSAGE
        }
    }
}

/**
 * [AccountDeletionCoordinator] の本番バインディング（Issue #51）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AccountDeletionCoordinatorModule {
    @Binds
    @Singleton
    abstract fun bindAccountDeletionCoordinator(
        impl: AccountDeletionCoordinatorImpl,
    ): AccountDeletionCoordinator
}
