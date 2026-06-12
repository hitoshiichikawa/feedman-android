package com.feedman.android.core.data

import com.feedman.android.core.model.User
import com.feedman.android.core.network.FeedmanApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UserRepository] の本実装（Issue #49 Req 1.2 / 2.1 / 4.1 / 5.1）。
 *
 * [FeedmanApi.getCurrentUser] への単純な委譲。[FeedmanApi] 経由でサーバー由来 / 通信失敗の
 * エラーは [com.feedman.android.core.network.FeedmanException] に変換済みのため、本実装は
 * 追加の例外変換を行わない。
 *
 * ## DI
 *
 * `RepositoryModule` で [UserRepository] にバインドされる（Hilt @Binds）。
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
) : UserRepository {

    override suspend fun getCurrentUser(): User {
        // FeedmanApi 経由でエラーは FeedmanException に変換済み（Issue #17）。
        // 認証切れは UNAUTHORIZED コードとして上位レイヤに透過される（Req 5.1）。
        return api.getCurrentUser()
    }

    override suspend fun deleteMe() {
        // Issue #51: SPEC §5.7 DELETE /api/users/me。
        // ネットワーク失敗 / サーバーエラーは FeedmanException として上位に透過する
        // （Issue #17 のエラー変換層が責務を担う）。本実装は薄い委譲のみ。
        api.deleteCurrentUser()
    }
}
