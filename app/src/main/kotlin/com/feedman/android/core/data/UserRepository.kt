package com.feedman.android.core.data

import com.feedman.android.core.model.User
import com.feedman.android.core.network.FeedmanException

/**
 * 現在ログイン中ユーザーの取得を担うデータ層境界（Issue #49 Req 1.2 / 2.1 / 2.2）。
 *
 * 本リポジトリは SPEC §4.2 の `GET /auth/me` を薄く委譲するだけのインターフェースで、
 * ViewModel（[com.feedman.android.feature.account.AccountSheetViewModel]）から
 * [FeedmanApi][com.feedman.android.core.network.FeedmanApi] の Retrofit 契約を切り離して
 * テスト境界（リポジトリの差し替え）を一定にする目的で抽象化される。
 *
 * ## エラーモデル
 *
 * 全 API 由来エラーは [FeedmanException] として透過される（Issue #17 のエラー変換層で
 * 統一済み）。呼び出し元は `code` を見て認証切れ（`UNAUTHORIZED`）と回復可能エラー
 * （`NETWORK_ERROR` 等）を分岐する（Issue #49 Req 4.1 / 5.1）。
 *
 * ## キャッシュ責務
 *
 * 本リポジトリ自身は in-memory cache を持たない（取得呼び出しごとに API を叩く）。
 * Issue #49 Req 1.4 の「同一セッション内のキャッシュ再利用」は ViewModel 側で表現する
 * （`AccountSheetViewModel` が成功 [User] を保持し、再 open で再フェッチしない）。
 */
interface UserRepository {

    /**
     * `GET /auth/me` を呼び、現在ログイン中ユーザーを返す。
     *
     * @return SPEC §4.2 で定義された [User]。
     * @throws FeedmanException サーバー由来 / 通信失敗 / レスポンス解析失敗を統一的に表現。
     *   認証切れは `code = "UNAUTHORIZED"` を持つ。
     */
    suspend fun getCurrentUser(): User

    /**
     * `DELETE /api/users/me` を呼び、現在ログイン中ユーザーの退会（全購読・既読/スター状態の
     * サーバー側削除）を実行する（Issue #51 / SPEC §5.7）。
     *
     * 本メソッドは **副作用を持つ不可逆操作** であり、呼び出し側はユーザーへの二段確認を
     * 完了した後にのみ呼び出すこと（Issue #51 Req 2.x）。サーバーからの 2xx 応答完了で
     * 正常終了する。ローカルクレデンシャル消去・キャッシュリセット・SessionState 遷移は
     * 呼び出し側（[com.feedman.android.core.auth.LogoutCoordinator] / 専用 Coordinator）の
     * 責務であり、本 Repository は API 呼び出しのみに責務を絞る。
     *
     * @throws FeedmanException サーバーエラー / ネットワーク失敗を統一的に表現
     *   （Issue #51 Req 5.1 / 5.3 / 5.4 のエラー通知契約）。code / errorMessage は
     *   [com.feedman.android.core.network.FeedmanException] の規約に従う。
     */
    suspend fun deleteMe()
}
