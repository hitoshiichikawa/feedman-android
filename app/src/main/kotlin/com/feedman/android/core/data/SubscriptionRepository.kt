package com.feedman.android.core.data

import com.feedman.android.core.model.Subscription
import kotlinx.coroutines.flow.Flow

/**
 * 購読フィードのデータソース境界（Issue #30 / Req 5.1, 5.3, 5.4）。
 *
 * ドロワーのフィード一覧（[com.feedman.android.shell.DrawerContent]）と購読設定シート
 * （#43）が共通で利用する抽象。実 API 統合（#39）の前段で UI を独立して実装するため、
 * 本インターフェースを Hilt 経由で [com.feedman.android.core.data.fake.FakeSubscriptionRepository]
 * にバインドする（NFR 3.1）。
 *
 * 実装規約:
 * - 戻り値の [Flow] は **購読開始時点で即座に**現在保持しているリストを 1 回流すこと（Req 5.3）。
 *   `flowOf` / `MutableStateFlow` などの「最新値を持つ」型を前提とした観測モデルに揃える
 *   ことで、UI 層は再描画ごとに pull せず `collectAsStateWithLifecycle` で受け取れる。
 * - 公開するフィードのフィールドは SPEC §4.2 `Subscription` をそのまま再利用する。
 *   `feed_id` / `feed_title` / `favicon_url` / `unread_count` / `feed_status` は Req 5.4 で
 *   要求される構造。
 */
interface SubscriptionRepository {
    /**
     * 購読フィードの現スナップショットを観測可能な [Flow] として返す（Req 5.1, 5.3）。
     *
     * 表示順は実装が保持する順序のままで返す。並び替え（Req 1.5）は UI 層・呼び出し側の責務外。
     */
    fun observeSubscriptions(): Flow<List<Subscription>>
}
