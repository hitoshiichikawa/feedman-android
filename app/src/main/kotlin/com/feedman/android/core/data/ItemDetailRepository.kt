package com.feedman.android.core.data

import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.network.FeedmanException

/**
 * 記事詳細取得と既読 / スター状態更新のデータ層境界（Issue #35 Req 1 / 2 / 3）。
 *
 * 記事詳細シート（#36）と楽観的更新同期（#38）など、上位の UI / 同期ロジックが共有して
 * 利用する SPEC §4.2 の `GET /api/items/{id}` と `PUT /api/items/{id}/state` のラッパー。
 * 本インターフェースは UI レイヤから FeedmanApi の Retrofit 契約を切り離し、テスト境界
 * （リポジトリの差し替え）を一定にする目的で抽象化される（NFR 1.1）。
 *
 * ## エラーモデル
 *
 * 全 API 由来エラーは [FeedmanException] として透過される（Issue #17 のエラー変換層で
 * 統一済み）。呼び出し元は `code` / `errorMessage` を見て楽観的更新のロールバック・
 * ユーザー表示を組み立てる（Issue #35 Req 3.1 / 3.2 / 3.3）。
 *
 * ## バリデーション規約
 *
 * [updateState] は `isRead` / `isStarred` の双方が `null` の場合、API を呼ばずに
 * バリデーションエラーとして [FeedmanException] を throw する（Issue #35 Req 2.5）。
 * これによりサーバー側に空ボディを送る無意味な往復を防ぎ、呼び出し元のバグを早期に
 * 露見させる。
 */
interface ItemDetailRepository {

    /**
     * 記事 ID から [ItemDetail]（`content` / `author` 含む）を取得する。
     *
     * @param itemId SPEC §4.2 の `id` フィールドに対応する記事 ID（ULID 文字列）。
     * @return SPEC §4.2 で定義された `ItemDetail`。
     * @throws FeedmanException サーバー由来 / 通信失敗 / レスポンス解析失敗を統一的に表現。
     */
    suspend fun getItem(itemId: String): ItemDetail

    /**
     * 既読 / スター状態を更新する（partial update）。
     *
     * - [isRead] と [isStarred] は片方ずつ、または両方を非 null で渡せる（Req 2.2 / 2.3 / 2.4）。
     * - null のフィールドはサーバー側に **送信されない**（フィールド省略 = 変更しない契約）。
     * - 両方とも `null` の場合は API を呼ばず、バリデーションエラーとして
     *   [FeedmanException]（[FeedmanException.CODE_UNKNOWN_ERROR]）を throw する（Req 2.5）。
     *
     * @param itemId 更新対象の記事 ID。
     * @param isRead 既読フラグの新しい値。`null` の場合は既読状態を変更しない。
     * @param isStarred スターフラグの新しい値。`null` の場合はスター状態を変更しない。
     * @throws FeedmanException サーバー由来 / 通信失敗 / 両方 null バリデーション失敗。
     *   呼び出し元はこの例外をキャッチして楽観的更新のロールバックを行う（Req 3.4）。
     */
    suspend fun updateState(itemId: String, isRead: Boolean?, isStarred: Boolean?)
}
