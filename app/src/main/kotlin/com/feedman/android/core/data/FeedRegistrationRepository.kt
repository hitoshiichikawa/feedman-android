package com.feedman.android.core.data

import com.feedman.android.core.model.Subscription
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.RegisterFeedRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * フィード登録リポジトリ（Issue #44 / Req 3.1 / Req 4.1 / Req 5.x / SPEC §5.5 / §4.3）。
 *
 * `POST /api/feeds`（SPEC §4.2）を呼び出してフィード登録要求をサーバーへ送信する。
 * URL の自動検出はサーバー側で行うため、本リポジトリはユーザー入力 URL 文字列を
 * そのまま送出する。
 *
 * ## エラー透過
 *
 * `FeedmanApi` 経路で非 2xx・I/O 失敗は network 層により [com.feedman.android.core.network.FeedmanException]
 * に変換済みのため、本リポジトリは例外型を分岐せずに `try/catch (FeedmanException)` で
 * 受ければ十分。UI 層（ViewModel）でユーザー向け文言を構築する責務分離（Req 5.1〜5.6）。
 *
 * - 409: 重複登録（[Req 5.1]）
 * - 429: 登録専用レート制限（[Req 5.3 / 5.4]）。`retryAfterSeconds` が含まれれば UI 層で時間案内
 * - 400 / 422: URL 不正・フィード未検出（[Req 5.2]）
 * - その他 4xx / 5xx: サーバー応答 `message` を優先、欠落時は汎用文言（[Req 5.5]）
 * - I/O 失敗（NETWORK_ERROR）: ネットワーク接続不可（[Req 5.6]）
 *
 * 上記の httpStatus 主導分岐は ViewModel 側で行う（design 判断: SPEC / SERVER に
 * 具体的なエラー `code` 文字列が未列挙のため）。
 *
 * ## DI
 *
 * `RepositoryModule` で `@Binds` により [FeedRegistrationRepositoryImpl] を本 interface に
 * バインドする（Fake 実装は現時点では不要 / v1 スコープでモック用途も AppConfig.mockMode から
 * 独立して扱う）。
 */
interface FeedRegistrationRepository {

    /**
     * フィード登録要求を送信する。
     *
     * 成功時は登録された購読フィードの [Subscription] を返す。
     * 失敗時は network 層が変換した [com.feedman.android.core.network.FeedmanException] を
     * 呼び出し元へ透過する（ViewModel 側で httpStatus / retryAfterSeconds / errorMessage を
     * 参照してユーザー向け文言を構築する）。
     *
     * @param url 登録対象 URL（http または https の絶対 URL を想定）。
     *   呼び出し側（ViewModel）でクライアント側バリデーション（http/https の構文チェック）を
     *   通過した値を渡す前提（Req 2.1）。
     * @return 登録された購読フィードのスナップショット
     */
    suspend fun register(url: String): Subscription
}

/**
 * [FeedRegistrationRepository] の本実装（Issue #44 / Req 3.1）。
 *
 * `POST /api/feeds` に [RegisterFeedRequest] を投げ、応答 [Subscription] をそのまま返す。
 * 例外変換は network 層（[com.feedman.android.core.network.FeedmanErrorMappingInterceptor]
 * + [com.feedman.android.core.network.FeedmanApiProxy]）が担うため、本実装は薄い委譲層。
 */
@Singleton
class FeedRegistrationRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
) : FeedRegistrationRepository {

    override suspend fun register(url: String): Subscription {
        return api.registerFeed(RegisterFeedRequest(url = url))
    }
}
