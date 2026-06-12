package com.feedman.android.shell

import com.feedman.android.core.model.Subscription

/**
 * ドロワーフィード行の表示モデル（Issue #30 / Req 1.2, 1.1.1, 1.1.2, 2.1, 2.2, 2.3）。
 *
 * UI（Composable）から状態判定ロジックを切り出すための純粋データクラス。
 * [Subscription] から [DrawerFeedRow.from] で派生する。
 *
 * @property feedId 行タップ時の遷移先 `feed/{feedId}` に渡す ID（Req 3.3）
 * @property title 表示するフィードタイトル
 * @property faviconValue Favicon Composable に渡す data URL または null
 * @property unreadCount 未読件数（0 を含む）
 * @property statusIcon 表示する状態アイコン種別。`None` のとき非表示（Req 2.3）
 * @property showUnreadBadge 未読バッジを表示するかどうか（Req 1.1.1 / 1.1.2）
 */
data class DrawerFeedRow(
    val feedId: String,
    val title: String,
    val faviconValue: String?,
    val unreadCount: Int,
    val statusIcon: FeedStatusIcon,
    val showUnreadBadge: Boolean,
) {
    companion object {
        /**
         * [Subscription] から表示用の [DrawerFeedRow] を構築する（Req 1.2, 1.1, 2.x）。
         *
         * - `feedStatus` 文字列から [FeedStatusIcon] を選択（Req 2.1〜2.3）
         * - 未読バッジは `unreadCount >= 1` のときに表示（Req 1.1.1 / 1.1.2）
         */
        fun from(subscription: Subscription): DrawerFeedRow = DrawerFeedRow(
            feedId = subscription.feedId,
            title = subscription.feedTitle,
            faviconValue = subscription.faviconUrl,
            unreadCount = subscription.unreadCount,
            statusIcon = FeedStatusIcon.from(subscription.feedStatus),
            showUnreadBadge = shouldShowUnreadBadge(subscription.unreadCount),
        )

        /**
         * 未読バッジの表示判定（Req 1.1.1 / 1.1.2）。
         * 未読件数が 1 以上のときに表示、0 以下のときは非表示。
         */
        fun shouldShowUnreadBadge(unreadCount: Int): Boolean = unreadCount >= 1
    }
}

/**
 * フィード行のまとまった a11y 読み上げに使う string resource ID を解決する純粋ロジック
 * （Issue #53 Req 1.2）。
 *
 * TalkBack は子孫の semantics を merge した上で 1 行として読み上げるため、行内の個別
 * contentDescription（favicon / 状態アイコン / 未読バッジ）をそれぞれ列挙すると音声が
 * 冗長になりやすい。本ロジックは `feed_status` と `unread_count` の組み合わせから
 * 「フィード名、状態、未読 N 件」の 1 文を構成するための string resource ID を返す。
 *
 * Composable 起動なしでテスト可能にするため、`R.string.*` の Int 値ではなく
 * [DrawerFeedRowA11yResource] 列挙を返し、Composable 側で stringResource(...) に展開する。
 */
internal object DrawerFeedRowA11y {
    /**
     * フィード行全体の a11y 読み上げに使う string resource ID を返す（Issue #53 Req 1.2）。
     *
     * 状態と未読件数の有無で 6 通り（active / stopped / error × unread あり / なし）を
     * 分岐する。未読 0 件のときはバッジ自体を描画しないため、文言からも未読件数を省く。
     */
    fun resolve(statusIcon: FeedStatusIcon, unreadCount: Int): DrawerFeedRowA11yResource {
        val hasUnread = unreadCount >= 1
        return when (statusIcon) {
            FeedStatusIcon.None -> if (hasUnread) {
                DrawerFeedRowA11yResource.ActiveWithUnread
            } else {
                DrawerFeedRowA11yResource.ActiveNoUnread
            }
            FeedStatusIcon.Stopped -> if (hasUnread) {
                DrawerFeedRowA11yResource.StoppedWithUnread
            } else {
                DrawerFeedRowA11yResource.StoppedNoUnread
            }
            FeedStatusIcon.Error -> if (hasUnread) {
                DrawerFeedRowA11yResource.ErrorWithUnread
            } else {
                DrawerFeedRowA11yResource.ErrorNoUnread
            }
        }
    }
}

/**
 * [DrawerFeedRowA11y.resolve] の戻り値（Issue #53 Req 1.2）。
 *
 * 各エントリは strings.xml の `drawer_feed_row_description_*` リソースに 1:1 対応する。
 * Composable から `stringResource(...)` で展開し、未読件数あり版には `unreadCount` を
 * format 引数として渡す。
 */
internal enum class DrawerFeedRowA11yResource(val hasUnreadArg: Boolean) {
    ActiveWithUnread(hasUnreadArg = true),
    ActiveNoUnread(hasUnreadArg = false),
    StoppedWithUnread(hasUnreadArg = true),
    StoppedNoUnread(hasUnreadArg = false),
    ErrorWithUnread(hasUnreadArg = true),
    ErrorNoUnread(hasUnreadArg = false),
}

/**
 * フィード行に表示する状態アイコンの種別（Issue #30 / Req 2.1, 2.2, 2.3）。
 *
 * `feedStatus` 文字列（SPEC §4.2 `Subscription.feed_status`）を UI 層が扱える
 * 厳密な列挙へ正規化する。
 */
enum class FeedStatusIcon {
    /** active（正常）— アイコン非表示（Req 2.3）。 */
    None,

    /** stopped — 停止アイコン（pause）を mutedFg で表示（Req 2.1）。 */
    Stopped,

    /** error — 警告アイコン（alert）を danger 色で表示（Req 2.2）。 */
    Error,

    ;

    companion object {
        /**
         * SPEC §4.2 の `feed_status` 文字列から [FeedStatusIcon] へ正規化する。
         *
         * 未知の値は安全側で [None] にフォールバック（v1 の SPEC §10 受け入れ基準で
         * 想定される値は active / stopped / error の 3 値のみ）。
         */
        fun from(feedStatus: String): FeedStatusIcon = when (feedStatus) {
            "stopped" -> Stopped
            "error" -> Error
            else -> None // "active" を含む未知値はアイコン非表示（Req 2.3）
        }
    }
}
