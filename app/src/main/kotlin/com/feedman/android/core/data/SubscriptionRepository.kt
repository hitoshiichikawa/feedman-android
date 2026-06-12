package com.feedman.android.core.data

import com.feedman.android.core.model.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 購読フィードのデータソース境界（Issue #30 + Issue #39 / Req 1, 2, 3, 4 / NFR 1.2）。
 *
 * ドロワーのフィード一覧（[com.feedman.android.shell.DrawerContent]）と購読設定シート
 * （#43）が共通で利用する抽象。
 *
 * #39 で実 API バインディング（[SubscriptionRepositoryImpl]）が追加され、`AppConfig.mockMode`
 * に応じて Fake と実装を切り替える運用に変わったが、本インターフェースの後方互換性は
 * 維持される（Req NFR 1.2: `DrawerViewModel` の利用箇所が機械的な書き換えなしに動作する）。
 *
 * ## 実装規約
 *
 * - 戻り値の [Flow] は **購読開始時点で即座に**現在保持しているリストを 1 回流すこと
 *   （Req 1.1.1）。実 API 実装は初期値として空リストを emit し、初回 fetch 完了後に置換
 *   する（DrawerContent の StateFlow.collectAsStateWithLifecycle と整合）。
 * - 公開するフィードのフィールドは SPEC §4.2 `Subscription` をそのまま再利用する。
 *   `feed_id` / `feed_title` / `favicon_url` / `unread_count` / `feed_status` は Req 1.3 で
 *   要求される構造。
 * - サーバーが返した順序（リスト index 順）を変えてはならない（Req 1.4）。
 *
 * ## #39 で追加された責務
 *
 * - [observeLoadState]: 取得状態（Loading / Success / Error）を観測可能にし、UI 側で
 *   フィードセクション内のエラー + 再試行表示を駆動する（Req 2.1, 2.2, 2.5, 2.6, 4.3）。
 * - [refresh]: 再試行 / pull-to-refresh 起点で `GET /api/subscriptions` を再呼び出しする
 *   （Req 2.4）。Fake では no-op で副作用なし。
 */
interface SubscriptionRepository {
    /**
     * 購読フィードの現スナップショットを観測可能な [Flow] として返す（Req 1.1, 1.2, 1.3, 1.4, 1.5）。
     *
     * 表示順は実装が保持する順序のままで返す。並び替えは UI 層・呼び出し側の責務外。
     * 取得失敗時は最後に成功した値（または初期空リスト）を保持し続け、エラー通知は
     * [observeLoadState] 側で行う（観測者が「リスト」と「状態」を独立に扱えるようにする
     * ための分離）。
     */
    fun observeSubscriptions(): Flow<List<Subscription>>

    /**
     * 直近の取得状態を観測可能な [Flow] として返す（Issue #39 / Req 2.1, 2.5, 4.3）。
     *
     * - Fake 実装は [SubscriptionLoadState.Success] のみを emit する（取得失敗は起きない）。
     * - 実 API 実装は Loading → Success/Error の遷移を反映する。
     */
    fun observeLoadState(): Flow<SubscriptionLoadState>

    /**
     * `GET /api/subscriptions` の再取得を要求する（Issue #39 / Req 2.4）。
     *
     * - Fake 実装は no-op（モックデータは静的）。
     * - 実 API 実装は新しい fetch を起動し、[observeLoadState] を Loading → Success/Error に
     *   進める。再取得中の例外は内部で捕捉して Error 状態として観測者に通知し、呼び出し元へ
     *   投げ返さない（UI 側の try/catch を不要にするための契約）。
     *
     * 並行呼び出しは実装側で in-flight 1 件に制限する（重複再取得の抑制）。
     */
    suspend fun refresh()

    /**
     * Issue #41 Req 4.1 / 4.2: 単一のフィードを feedId（`Subscription.feedId`）で
     * 絞り込み観測する補助メソッド。フィード別画面（FeedScreen）はこのストリームから
     * フィード名・status・error_message を取り出して警告バナーとタイトルを描画する。
     *
     * - [observeSubscriptions] と同じくセッションキャッシュに依存するため、購読開始時点で
     *   即時に現在の値（一致する Subscription または `null`）を 1 度流す。
     * - 一致する Subscription が見つからない場合は `null` を流す（Req 4.3 のフィード未存在
     *   表示判定）。
     * - 値はサーバ取得の更新（[refresh] / [resume] 等）に追従して再 emit される。
     *
     * 既定実装は [observeSubscriptions] を `map { firstOrNull }` で射影する形にしておくため、
     * 既存の Fake / 実装は本メソッドを再実装しなくても契約を満たす（Req NFR 1.1 後方互換）。
     */
    fun observeFeed(feedId: String): Flow<Subscription?> =
        observeSubscriptions().mapToSingleByFeedId(feedId)

    /**
     * Issue #41 Req 3.5 / 3.7 / 3.8: 停止 / エラー状態のフィードを再開する。
     *
     * SPEC §4.2 `POST /api/subscriptions/{id}/resume` を呼び出す。成功時は Subscription
     * の最新スナップショットを内部状態へ反映し、[observeSubscriptions] / [observeFeed] が
     * 新しい状態（active）を流す（Req 3.7）。失敗時は例外を呼び出し元へ投げ返す（UI 側で
     * snackbar 表示するため。Req 3.8）。
     *
     * @param subscriptionId 対象 [com.feedman.android.core.model.Subscription.id]（パス上の
     *   `{id}` に対応）。本メソッドは feed_id と subscription_id を取り違えないため、
     *   呼び出し側が Subscription を観測してから `.id` を渡す前提とする。
     * @return 再開後の最新 Subscription（active 化された状態）
     */
    suspend fun resume(subscriptionId: String): Subscription =
        throw UnsupportedOperationException(
            "SubscriptionRepository.resume はこの実装でサポートされていません",
        )

    /**
     * Issue #42 Req 1.1 / 2.1 / 2.3: 当該フィードの手動フェッチを要求する。
     *
     * SPEC §4.2 `POST /api/subscriptions/{id}/fetch` を呼び出す。成功時は当該購読の
     * 最新スナップショット（unread_count 等を含む）を返し、内部状態へ反映する。これにより
     * [observeSubscriptions] / [observeFeed] を購読中の UI（ドロワー / FeedScreen）が新しい
     * unread バッジ等を観測する（Req 2.3 ドロワー未読バッジ反映）。
     *
     * 失敗時は [com.feedman.android.core.network.FeedmanException] をそのまま呼び出し元へ
     * 投げ返す（UI 側で snackbar 表示する。Req 3.1 / 4.1）。クールダウン応答（429 /
     * `FEED_COOLDOWN`）は code = `FEED_COOLDOWN`・retryAfterSeconds を含む形で観測される
     * （Req 3.1 / 3.2 / 3.3）。
     *
     * 既定実装は [UnsupportedOperationException] を投げる（Fake 実装が選択的に override
     * できるようにするための互換性確保）。
     *
     * @param subscriptionId 対象 [com.feedman.android.core.model.Subscription.id]（パス上の
     *   `{id}` に対応）
     * @return フェッチ後の最新 Subscription
     */
    suspend fun fetch(subscriptionId: String): Subscription =
        throw UnsupportedOperationException(
            "SubscriptionRepository.fetch はこの実装でサポートされていません",
        )
}

/**
 * `Flow<List<Subscription>>` から単一フィードを抽出するための内部ヘルパー
 * （Issue #41 Req 4.1 / 4.3 / NFR 1.1）。
 *
 * `observeFeed(feedId)` の既定実装に使用される。`distinctUntilChanged` を併用することで、
 * 一覧側の他フィード変更で再 emit されないようにする（NFR 1.1 応答性のため）。
 */
internal fun Flow<List<Subscription>>.mapToSingleByFeedId(
    feedId: String,
): Flow<Subscription?> =
    this.map { list -> list.firstOrNull { it.feedId == feedId } }.distinctUntilChanged()
