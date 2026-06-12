package com.feedman.android.core.data

import com.feedman.android.core.model.Subscription
import kotlinx.coroutines.flow.Flow

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
}
