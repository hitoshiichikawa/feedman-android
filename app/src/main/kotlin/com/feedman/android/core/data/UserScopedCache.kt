package com.feedman.android.core.data

/**
 * ユーザースコープの in-memory 状態を保持するキャッシュ境界（Issue #50 / Req 3.1）。
 *
 * ログアウト時に前ユーザーの既読・スター等の状態が新セッションに引き継がれて
 * しまうのを防ぐため、`LogoutCoordinator` が確定タイミングで `reset()` を呼ぶ。
 * 実装はプロセス内メモリ上の Map / StateFlow / 単一値などを初期状態に戻す責務を持つ。
 *
 * ## 設計判断（明示列挙）
 *
 * `requirements.md` の Open Question で挙げられている「ユーザースコープでリセット
 * 対象とすべきキャッシュ」は本 Issue 着手時点では以下に限定して明示列挙する:
 *
 * - [ItemStateStore]: 既読・スターの overlay と失敗イベント
 * - [SubscriptionRepositoryImpl]: 購読フィードリストと取得状態
 * - [CrossFeedRepositoryImpl]: セッション固定の `since_time`
 *
 * `UserRepository` 自体は in-memory cache を持たない（Issue #49 設計）。`AccountSheetViewModel`
 * の `cachedUser` は ViewModel スコープ内に閉じているため、ログアウト確定後に
 * ViewModel が再生成されるか、ViewModel 自身がリセットされる経路（ログアウト処理
 * 起点でフィールドをクリアする）で破棄する。
 *
 * ## 拡張方針
 *
 * 新たにユーザースコープの in-memory キャッシュを追加する場合は、本インターフェースを
 * 実装した上で `LogoutCoordinator` のコンストラクタに加える明示列挙方式を維持する
 * （multibinding にせず、依存リストとして可視化することでリセット漏れを発見しやすくする）。
 *
 * ## 実装責務
 *
 * - [reset] は冪等であること（複数回呼ばれても安全）
 * - [reset] は I/O を行わず、メモリ上の状態のみを初期化する（NFR 1.2 への寄与）
 * - [reset] 内で例外を投げないこと（best-effort パス上で呼ばれるため）
 */
interface UserScopedCache {
    /**
     * ユーザースコープ状態を初期状態に戻す（Req 3.1）。
     *
     * 本メソッドは suspend だが、I/O を発生させず CPU 上で完結する想定。
     * 例外を投げた場合は呼び出し側（`LogoutCoordinator`）が握り潰し、後続のリセット・
     * ログイン画面遷移を継続する（Req 5.1 / NFR 2.1）。
     */
    suspend fun reset()
}
