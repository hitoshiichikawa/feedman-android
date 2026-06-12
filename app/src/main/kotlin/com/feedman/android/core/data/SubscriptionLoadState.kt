package com.feedman.android.core.data

/**
 * 購読フィード一覧の取得状態（Issue #39 / Req 2.1, 2.2, 2.5, 4.3）。
 *
 * `SubscriptionRepository.observeLoadState()` が emit する状態の型。ドロワー UI 側は
 * 当該 state を購読し、フィードセクション領域の表示（ローディング / 成功 / エラー +
 * 再試行）を切り替える。
 *
 * - [Idle]: まだ一度も取得を試みていない初期状態（テスト容易性のため明示する）。
 * - [Loading]: `GET /api/subscriptions` の初回呼び出し or refresh による再取得が進行中
 *   （Req 2.5）。フィードセクションに「ロード中」表示。
 * - [Success]: 直近の取得が 2xx 成功。`observeSubscriptions()` 側が最新リストを保持している。
 * - [Error]: 直近の取得が失敗（非 2xx またはネットワーク失敗）。UI 表示用の [message] と
 *   サーバー由来の `code`（あれば）を保持し、フィードセクションにエラー + 再試行を提示
 *   する（Req 2.1, 2.2, 2.6）。
 *
 * 認証エラー（401 → 共通認証層のリフレッシュ後も継続失敗）は [Error] として表現する
 * （Req 4.3）。code を保持しているので将来 UI 側で 401 のみの専用導線を分岐できる。
 */
sealed interface SubscriptionLoadState {

    /** 初期状態。まだ取得を試みていない。 */
    data object Idle : SubscriptionLoadState

    /** 取得進行中。フィードセクションにロード中表示を出す（Req 2.5）。 */
    data object Loading : SubscriptionLoadState

    /** 取得成功（最新リストは observeSubscriptions() 側）。 */
    data object Success : SubscriptionLoadState

    /**
     * 取得失敗（Req 2.1, 2.6, 4.3）。
     *
     * @property message ユーザー向け表示文言。サーバーが SPEC §4.3 統一エラーの `message` を
     *   返した場合はそれを用いる（Req 2.6）。それ以外（ネットワーク失敗・パース失敗）は
     *   フォールバック文言（`FeedmanException.FALLBACK_*_MESSAGE`）。空文字にはしない。
     * @property code サーバー由来の `code`（SPEC §4.3）または合成 code（`NETWORK_ERROR` /
     *   `UNKNOWN_ERROR`）。401 など将来 UI 側で分岐したい場合の手掛かりとして残す。
     */
    data class Error(
        val message: String,
        val code: String,
    ) : SubscriptionLoadState
}
