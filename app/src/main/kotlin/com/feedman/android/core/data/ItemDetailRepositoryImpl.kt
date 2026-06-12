package com.feedman.android.core.data

import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.network.ItemStateUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ItemDetailRepository] の本実装（Issue #35）。
 *
 * [FeedmanApi.getItem] / [FeedmanApi.updateItemState] へのシンプルな委譲だが、
 * 既読 / スター状態更新の **両方とも null** という不正呼び出しに対するバリデーションを
 * 持つ（Req 2.5）。サーバー由来 / 通信失敗のエラーは [FeedmanApi] 経由で
 * [FeedmanException] に変換済みのため、本実装は追加の例外変換を行わない（Req 3.1 / 3.2 / 3.3）。
 *
 * ## 副作用
 *
 * 本実装はローカルキャッシュ・状態保持を持たない（v1 スコープ外 / SPEC §1.3 整合）。
 * [updateState] が例外を投げた時点で副作用は残らず、呼び出し元の楽観的更新ロールバックは
 * 上位レイヤーの単一責務として閉じる（Req 3.4）。
 *
 * ## DI
 *
 * `RepositoryModule` で [ItemDetailRepository] にバインドされる（Hilt @Binds）。
 */
@Singleton
class ItemDetailRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
) : ItemDetailRepository {

    override suspend fun getItem(itemId: String): ItemDetail {
        // FeedmanApi 経由でエラーは FeedmanException に変換済み（Req 1.1 / 1.2 / 3.1）。
        return api.getItem(itemId = itemId)
    }

    override suspend fun updateState(itemId: String, isRead: Boolean?, isStarred: Boolean?) {
        // Req 2.5: 両方 null は API を呼ばずバリデーションエラーとして throw する。
        if (isRead == null && isStarred == null) {
            throw FeedmanException(
                code = FeedmanException.CODE_UNKNOWN_ERROR,
                errorMessage = "updateState requires at least one of isRead / isStarred to be non-null",
            )
        }

        // ItemStateUpdateRequest の null フィールドは Json.explicitNulls=false の効果で
        // body から省略される（Req 2.2 / 2.3）。両方非 null の場合は両キーが乗る（Req 2.4）。
        val request = ItemStateUpdateRequest(isRead = isRead, isStarred = isStarred)
        // 戻り値（CrossFeedItem）はリポジトリ呼び出し元では用いない。サーバーが 2xx を返した
        // ことだけが Req 2.6 の「成功通知」の根拠となる。エラーは FeedmanApi 経由で
        // FeedmanException に変換済み（Req 3.2 / 3.3）。
        api.updateItemState(itemId = itemId, request = request)
    }
}
