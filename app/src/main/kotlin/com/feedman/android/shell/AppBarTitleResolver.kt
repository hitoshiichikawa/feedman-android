package com.feedman.android.shell

/**
 * 上部アプリバーに表示するタイトル / サブタイトル（Issue #31 / Req 1.1〜1.5）。
 *
 * `design/mobile/fm-screens.jsx` の `FMHeader` が受け取る `title` / `sub` をモデル化したもの。
 * - [title]: 1 行目に大きく表示するタイトル
 * - [subtitle]: タイトル直下に薄字で表示する補助文字列。指定がなければ `null`
 */
data class AppBarTitle(
    val title: String,
    val subtitle: String? = null,
)

/**
 * 現在ルートに応じて表示するタイトル/サブタイトルを解決する純粋関数（Issue #31 / Req 1.1〜1.5）。
 *
 * ルート ID と「feedId → フィード名解決関数」を入力にとり、ルートごとのタイトルを返す:
 *
 * - `timeline` → 「すべての新着」相当（Req 1.1）
 * - `starred`  → 「お気に入り」相当（Req 1.2）
 * - `feed/{feedId}` → 個別フィード名（Req 1.3）。`feedTitleLookup` が `null` を返した場合は
 *   フォールバック文字列 [fallbackFeedTitle] を採用する（未解決時の表示安全装置）
 * - `search` → 検索画面の表示文字列。検索画面はトップバー自体が検索入力フィールドに置き換わる
 *   想定だが、本 Issue では placeholder 配線のため通常タイトルを返しておく
 *
 * Composable 起動なしで JVM 単体テスト対象とするため、UI 層から分離した純粋関数として公開する。
 *
 * @param routeId 現在表示中のルート ID。`feed/<feedId>` のように展開済みのパスを受け取る場合と、
 *   テンプレート `feed/{feedId}` を受け取る場合の双方を扱えるよう、prefix 一致で判定する。
 * @param feedIdExtractor `routeId` から feedId を抽出する関数。具体的なパスから feedId を
 *   取り出す責務は呼び出し側（NavController 観測側）に委ねる。
 * @param feedTitleLookup feedId を渡すとフィード名を返す解決関数。`null` 戻りはフィード名
 *   未解決状態（リポジトリが空 / 取得未完了など）を示す。
 * @param strings ルート別の固定文言（タイトル / フォールバック）を文字列リソースから注入する。
 *
 * @return [AppBarTitle]。サブタイトルが定義されていなければ `subtitle = null`。
 */
fun resolveAppBarTitle(
    routeId: String,
    feedIdExtractor: (String) -> String? = ::extractFeedIdFromRouteId,
    feedTitleLookup: (String) -> String? = { null },
    strings: AppBarStrings,
): AppBarTitle = when {
    routeId == AppRoute.ROUTE_TIMELINE -> AppBarTitle(title = strings.timelineTitle)
    routeId == AppRoute.ROUTE_STARRED -> AppBarTitle(title = strings.starredTitle)
    routeId == AppRoute.ROUTE_SEARCH -> AppBarTitle(title = strings.searchTitle)
    routeId.startsWith("feed/") -> {
        val feedId = feedIdExtractor(routeId)
        val title = feedId?.let(feedTitleLookup) ?: strings.feedFallbackTitle
        AppBarTitle(title = title)
    }
    // 想定外ルート ID は安全側に倒し、タイムラインの文言を返す（Req 1.5 のフォールバック）
    else -> AppBarTitle(title = strings.timelineTitle)
}

/**
 * `feed/<feedId>` 形式のルート ID から feedId を抽出する（Issue #31 / Req 1.3）。
 *
 * - `feed/abc-123` → `"abc-123"`
 * - `feed/{feedId}`（テンプレート） → `"{feedId}"`（呼び出し側で解決失敗として扱う）
 * - prefix が `feed/` でない / 空 feedId → `null`
 */
fun extractFeedIdFromRouteId(routeId: String): String? {
    if (!routeId.startsWith("feed/")) return null
    val rest = routeId.removePrefix("feed/")
    if (rest.isEmpty()) return null
    // テンプレートそのまま（`{feedId}`）は未解決として `null` 扱いにする
    if (rest == "{feedId}") return null
    return rest
}

/**
 * トップアプリバーに差し込む文字列リソース束（Issue #31 / Req 1.1, 1.2, 1.3）。
 *
 * Composable 側から `stringResource(...)` で解決した値をまとめて渡すことで、
 * [resolveAppBarTitle] を Android 依存なしの JVM テストから検証できるようにする。
 */
data class AppBarStrings(
    val timelineTitle: String,
    val starredTitle: String,
    val searchTitle: String,
    /** 個別フィード画面で feedId からフィード名を解決できなかった場合のフォールバック表示。 */
    val feedFallbackTitle: String,
)
