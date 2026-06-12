package com.feedman.android.core.data.fake

import com.feedman.android.core.data.SubscriptionLoadState
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.model.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 表示確認用の [SubscriptionRepository] Fake 実装（Issue #30 + Issue #39 / Req 3.1, 3.2, 3.4）。
 *
 * `design/mobile/fm-data.jsx` のモックフィード一覧を Android 側で再現したサンプルデータ
 * を返す。状態（active / stopped / error）・favicon（data URL / null）・未読件数（0 含む）の
 * 各バリエーションを最低 1 件ずつ含め、ドロワー UI の全分岐を視覚確認できるようにする。
 *
 * - Issue #30 Req 5.2: active / stopped / error の各状態を 1 件以上含む。
 * - Issue #30 Req 5.3: 購読開始時点で即座にリストを 1 度流す（`flowOf` の単発 emit で satisfy）。
 * - Issue #30 Req 1.5: リポジトリが返す順序＝表示順序。本リストの定義順がそのままドロワー表示順。
 * - Issue #39 Req 3.1, 3.2: `AppConfig.mockMode = true` のときに DI から本実装が解決される。
 *   `GET /api/subscriptions` は呼び出さない（Retrofit に依存しない）。
 * - Issue #39 Req 3.4: 公開インターフェースは実 API 実装と同一を保つ（[observeLoadState] /
 *   [refresh] を追加実装するが、Fake では取得失敗が起きないため [SubscriptionLoadState.Success]
 *   のみ emit し、[refresh] は no-op とする）。
 */
@Singleton
class FakeSubscriptionRepository @Inject constructor() : SubscriptionRepository {

    override fun observeSubscriptions(): Flow<List<Subscription>> = flowOf(MOCK_SUBSCRIPTIONS)

    /**
     * Fake では取得失敗が起きないため、常に [SubscriptionLoadState.Success] のみを emit する
     * （Issue #39 Req 3.4: 公開 IF 互換）。
     */
    override fun observeLoadState(): Flow<SubscriptionLoadState> =
        flowOf(SubscriptionLoadState.Success)

    /**
     * Fake では再取得しても結果が変わらないため no-op（Issue #39 Req 3.2: API を呼ばない）。
     * suspend 契約は維持し、呼び出し側の `viewModelScope.launch { repo.refresh() }` がそのまま
     * 動くようにする。
     */
    override suspend fun refresh() {
        // no-op: モックデータは静的
    }

    /**
     * Issue #41: Fake では再開しても結果が変わらないため、対象 Subscription を `active` に
     * 切り替えた仮想スナップショットを返す（モック UI 確認で「再開」ボタン挙動を疑似体験
     * できるようにする）。実際の状態 emit は行わない（モックリストは静的なため）。
     *
     * @throws IllegalStateException 指定 ID のモックフィードが見つからないとき（呼び出し
     *   バグの早期検出）
     */
    override suspend fun resume(subscriptionId: String): Subscription {
        val target = MOCK_SUBSCRIPTIONS.firstOrNull { it.id == subscriptionId }
            ?: error("FakeSubscriptionRepository: subscriptionId=$subscriptionId が見つかりません")
        return target.copy(feedStatus = "active", errorMessage = null)
    }

    companion object {
        // 1x1 PNG（青）の data URL。`design/SPEC.md` §4.4 に従い `data:<mime>;base64,...` 形式。
        // Favicon Composable（#26）が data URL 経由で画像描画する経路を確認できる。
        private const val FAVICON_PNG_BLUE: String =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

        // 1x1 PNG（オレンジ）の data URL。レターアバター fallback ではなく実画像描画経路用。
        private const val FAVICON_PNG_ORANGE: String =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

        internal val MOCK_SUBSCRIPTIONS: List<Subscription> = listOf(
            Subscription(
                id = "s1",
                userId = "u-mock",
                feedId = "f1",
                feedTitle = "Publickey",
                feedUrl = "https://www.publickey1.jp/atom.xml",
                faviconUrl = FAVICON_PNG_BLUE,
                fetchIntervalMinutes = 30,
                feedStatus = "active",
                errorMessage = null,
                unreadCount = 12,
                createdAt = "2025-05-01T09:00:00Z",
            ),
            Subscription(
                id = "s2",
                userId = "u-mock",
                feedId = "f2",
                feedTitle = "Zenn トレンド",
                feedUrl = "https://zenn.dev/feed",
                faviconUrl = null, // letter avatar fallback
                fetchIntervalMinutes = 30,
                feedStatus = "active",
                errorMessage = null,
                unreadCount = 5,
                createdAt = "2025-05-02T09:00:00Z",
            ),
            Subscription(
                id = "s3",
                userId = "u-mock",
                feedId = "f3",
                feedTitle = "はてブ テクノロジー",
                feedUrl = "https://b.hatena.ne.jp/hotentry/it.rss",
                faviconUrl = FAVICON_PNG_ORANGE,
                fetchIntervalMinutes = 30,
                feedStatus = "active",
                errorMessage = null,
                unreadCount = 28,
                createdAt = "2025-05-03T09:00:00Z",
            ),
            Subscription(
                id = "s4",
                userId = "u-mock",
                feedId = "f4",
                feedTitle = "ITmedia NEWS",
                feedUrl = "https://www.itmedia.co.jp/news/rss/news_bursts.xml",
                faviconUrl = null,
                fetchIntervalMinutes = 60,
                feedStatus = "active",
                errorMessage = null,
                unreadCount = 0, // 未読バッジ非表示（Req 1.1.2）
                createdAt = "2025-05-04T09:00:00Z",
            ),
            Subscription(
                id = "s5",
                userId = "u-mock",
                feedId = "f5",
                feedTitle = "The Go Blog",
                feedUrl = "https://go.dev/blog/feed.atom",
                faviconUrl = null,
                fetchIntervalMinutes = 360,
                feedStatus = "active",
                errorMessage = null,
                unreadCount = 2,
                createdAt = "2025-05-05T09:00:00Z",
            ),
            Subscription(
                id = "s6",
                userId = "u-mock",
                feedId = "f6",
                feedTitle = "Qiita 人気の記事",
                feedUrl = "https://qiita.com/popular-items/feed",
                faviconUrl = null,
                fetchIntervalMinutes = 30,
                feedStatus = "stopped",
                errorMessage = "手動で停止しました",
                unreadCount = 14,
                createdAt = "2025-05-06T09:00:00Z",
            ),
            Subscription(
                id = "s7",
                userId = "u-mock",
                feedId = "f7",
                feedTitle = "GIGAZINE",
                feedUrl = "https://gigazine.net/news/rss_2.0/",
                faviconUrl = null,
                fetchIntervalMinutes = 60,
                feedStatus = "error",
                errorMessage = "404 Not Found（フィードが見つかりません）",
                unreadCount = 0,
                createdAt = "2025-05-07T09:00:00Z",
            ),
        )
    }
}
