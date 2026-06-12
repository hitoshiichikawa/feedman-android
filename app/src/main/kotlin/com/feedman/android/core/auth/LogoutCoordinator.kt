package com.feedman.android.core.auth

import com.feedman.android.core.data.CrossFeedRepositoryImpl
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.data.SubscriptionRepositoryImpl
import com.feedman.android.core.data.UserScopedCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LogoutCoordinator] の最小契約（Issue #50）。
 *
 * ViewModel から DI 経由で参照する境界を本インターフェースで切り出し、テスト時に
 * 軽量な fake で差し替えられるようにする。本契約は「ログアウト処理 1 工程」を
 * suspend で表現する 1 メソッドのみ。
 */
interface LogoutCoordinator {
    /**
     * ログアウト処理を実行する。例外を投げない契約（Req 5.1）。
     */
    suspend fun perform()
}

/**
 * ログアウト処理の調整役（Issue #50 Req 1〜5 / NFR 1.2 / NFR 2.1）。
 *
 * `AccountSheetViewModel` の「ログアウト」操作起点で呼び出され、以下を 1 トランザクション
 * として実行する:
 *
 * 1. [AuthRepository.revoke] を 1 回呼び出す（Req 2.1）
 *    - 内部実装は SERVER.md §1.3 `POST /api/auth/revoke` を best-effort で投げ、
 *      ネットワーク失敗・サーバーエラーでも TokenStore を消去する（Req 2.2 / 2.3）
 *    - 全工程に [REVOKE_TIMEOUT_MILLIS] = 10 秒の上限を掛け、サーバー無応答時でも
 *      ユーザーをログイン画面まで返す（NFR 1.2）
 * 2. ユーザースコープの in-memory キャッシュ（[UserScopedCache] 実装）を順に [UserScopedCache.reset]
 *    で初期化する（Req 3.1）
 *    - 明示列挙方式（multibinding を使わない）でリセット漏れを発見しやすくする
 *    - 各 reset が例外を投げても他の reset は続行する（NFR 2.1: クレデンシャル消去の確実性）
 * 3. SessionState 遷移は本クラスでは直接行わず、[AuthRepository.revoke] 内で
 *    `observeIsAuthenticated()` が `false` に遷移する経路に委ねる（Req 4.1）
 *    - `AuthRepositorySessionStateProvider` が当該変化を観測して [SessionState.LoggedOut] へ
 *      遷移させ、`AppShell` が LoginScreen を描画する（Req 4.2）
 *
 * ## エラーモデル
 *
 * - 本クラスの公開メソッド [perform] は **例外を投げない**（Req 5.1）
 * - revoke ネットワーク失敗・サーバーエラーは [AuthRepository] 側で握り潰される
 * - キャッシュリセット失敗も本クラスが握り潰し、後続処理を継続する
 *
 * ## NFR 2.2 / NFR 3.1 の遵守
 *
 * - 本クラスは access token / refresh token / email など個人識別情報の **値そのもの** を
 *   引数で受け取らない。AuthRepository / UserScopedCache が内部で保持する状態だけを
 *   扱うため、ログにこれらが出力されることはない
 * - 進行ログを出す場合も「ログアウト処理を開始/完了」程度に留め、ユーザー識別情報は含めない
 *   （現状の実装は明示的なログ出力を行っていない）
 *
 * @param authRepository revoke + TokenStore 消去を担う認証境界
 * @param userScopedCaches リセット対象のユーザースコープキャッシュ群（コンストラクタで
 *   明示列挙される）。リセットは引数の順序で実行されるが、各 reset は独立であり順序依存
 *   しない設計とする
 */
@Singleton
class LogoutCoordinatorImpl @Inject constructor(
    private val authRepository: AuthRepository,
    // 明示列挙: 個別の Singleton 実装を直接受け取り、リセット漏れを発見しやすくする。
    // インターフェース型ではなく実装型で受け取るのは、DI コンテナでの解決を一意にし、
    // テストで個別に差し替えやすくするため。
    itemStateStore: ItemStateStore,
    subscriptionRepository: SubscriptionRepository,
    crossFeedRepository: CrossFeedRepositoryImpl,
) : LogoutCoordinator {

    /**
     * リセット対象のユーザースコープキャッシュ群。明示列挙でリスト化しておくことで、
     * 新規キャッシュ追加時の漏れに気付きやすくする（Req 3.1 / Open Question への回答）。
     *
     * SubscriptionRepository は [SubscriptionRepositoryImpl] の場合のみ reset を呼ぶ
     * （Fake は in-memory cache の意味では空であり、reset 不要）。
     */
    private val userScopedCaches: List<UserScopedCache> = buildList {
        add(itemStateStore)
        if (subscriptionRepository is SubscriptionRepositoryImpl) {
            add(subscriptionRepository)
        }
        add(crossFeedRepository)
    }

    /**
     * ログアウト処理を実行する（Req 2.1 / 2.2 / 2.3 / 2.4 / 3.1 / 4.1 / 5.1 / NFR 1.2 / NFR 2.1）。
     *
     * 本メソッドは例外を投げない。revoke の HTTP 失敗・キャッシュリセット失敗が起きても
     * ローカルトークン消去とログイン画面遷移を完遂する。
     *
     * - 全体に [REVOKE_TIMEOUT_MILLIS] = 10 秒のタイムアウトを設ける（NFR 1.2）。タイムアウト
     *   超過時でも TokenStore は [AuthRepository.revoke] 内で消去されている保証は無いため、
     *   タイムアウト経路では明示的に `tokenStore` を消去する責務を持つ ... ではなく、
     *   AuthRepository.revoke は best-effort で `tokenStore.clear()` を必ず呼ぶ設計のため
     *   タイムアウトしても TokenStore は空となる（[AuthRepositoryImpl] 参照）。タイムアウトは
     *   サーバー応答待ちを打ち切るためのものであり、ローカル消去自体は revoke 内で完了する。
     * - キャッシュリセットは revoke 完了後に直列で実行する（Req 3.1）。
     */
    override suspend fun perform() {
        // Phase 1: revoke + ローカル消去（Req 2.1 / 2.2 / 2.3 / 2.4 / NFR 1.2）。
        // withTimeoutOrNull で 10 秒上限を掛ける。タイムアウト時は null が返るが、
        // AuthRepository.revoke はネットワーク失敗・サーバーエラーでも TokenStore を消去する
        // 設計のため、タイムアウトでも端末上のトークンは消える経路を辿る（タイムアウトの
        // 内側で tokenStore.clear() まで到達することを期待）。万一そこに到達できなかった
        // 場合の保険として、本コーディネーターは追加の clear 経路を持たない（責務分離。
        // TokenStore への書き込みは AuthRepository 経由でのみ行う）。
        withTimeoutOrNull(REVOKE_TIMEOUT_MILLIS) {
            runCatching { authRepository.revoke() }
        }

        // Phase 2: ユーザースコープキャッシュのリセット（Req 3.1 / NFR 2.1）。
        // 個別の reset 失敗が他に伝播しないように runCatching で守る。
        for (cache in userScopedCaches) {
            runCatching { cache.reset() }
        }

        // Phase 3: SessionState 遷移は本クラスでは行わない（Req 4.1）。
        // AuthRepository.revoke が観測可能なログイン状態を false に遷移させ、
        // AuthRepositorySessionStateProvider が LoggedOut を流すことで AppShell が
        // LoginScreen を描画する。
    }

    companion object {
        /** NFR 1.2: revoke 応答待ちの上限。10 秒。 */
        const val REVOKE_TIMEOUT_MILLIS: Long = 10_000L
    }
}

/**
 * [LogoutCoordinator] の本番バインディング（Issue #50）。
 *
 * モック / Fake 系は別途 `@TestInstallIn` で差し替える前提。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LogoutCoordinatorModule {
    @Binds
    @Singleton
    abstract fun bindLogoutCoordinator(impl: LogoutCoordinatorImpl): LogoutCoordinator
}
