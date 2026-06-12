package com.feedman.android.shell

/**
 * App Shell が NavHost に宣言する 4 ルートの定義（Issue #29 / Req 1.2）。
 *
 * `requirements.md` Requirement 1.2 のとおり、App Shell は以下の 4 ルートのみを宣言し、
 * それ以外（記事詳細・フィード登録・購読設定・アカウント）はボトムシート前提として
 * ルートに含めない（Req 1.5、SPEC §5.0 / GRAND-DESIGN §5.6）:
 *
 * - `timeline` 横断新着タイムライン（起動時の初期ルート / Req 1.1）
 * - `feed/{feedId}` フィード別記事一覧（パスパラメータ `feedId` / Req 1.3）
 * - `starred` お気に入り一覧
 * - `search` 横断検索
 *
 * sealed class により、ルート ID の参照を文字列リテラル散在ではなく型を介して行うことで、
 * `feed/{feedId}` のテンプレートと具体的な遷移先パス生成（[Feed.path]）の表現を分離する。
 */
sealed class AppRoute(val id: String) {

    /** 横断新着タイムライン。起動時の初期ルート（Req 1.1）。 */
    data object Timeline : AppRoute(ROUTE_TIMELINE)

    /**
     * フィード別記事一覧（Req 1.3）。
     *
     * NavHost に登録するルートテンプレートは `feed/{feedId}` であり、`{feedId}` は
     * `NavBackStackEntry.arguments` から取り出す。実際の遷移時には [path] で
     * `feed/<feedId>` に展開した文字列を `navController.navigate` に渡す。
     */
    data object Feed : AppRoute(ROUTE_FEED_TEMPLATE) {
        /** ルート引数名（`feed/{feedId}` の `{feedId}` 部分）。 */
        const val ARG_FEED_ID: String = "feedId"

        /**
         * 指定 [feedId] への遷移パスを返す（例: `feed/abc-123`）。
         *
         * 空 [feedId] は呼び出し側のバグ（UI から無効値で navigate を要求された）なので
         * `IllegalArgumentException` を投げて検出する。
         */
        fun path(feedId: String): String {
            require(feedId.isNotEmpty()) { "feedId must not be empty" }
            return "feed/$feedId"
        }
    }

    /** お気に入り一覧。 */
    data object Starred : AppRoute(ROUTE_STARRED)

    /** 横断検索。 */
    data object Search : AppRoute(ROUTE_SEARCH)

    companion object {
        const val ROUTE_TIMELINE: String = "timeline"
        const val ROUTE_FEED_TEMPLATE: String = "feed/{feedId}"
        const val ROUTE_STARRED: String = "starred"
        const val ROUTE_SEARCH: String = "search"

        /**
         * App Shell が宣言する 4 ルートの ID 集合（Req 1.2）。
         *
         * テストで「宣言ルートが 4 件・かつ列挙が変わっていないこと」を検証するために、
         * sealed class の派生から導出する固定リストを公開する。
         */
        val declaredRouteIds: List<String> = listOf(
            ROUTE_TIMELINE,
            ROUTE_FEED_TEMPLATE,
            ROUTE_STARRED,
            ROUTE_SEARCH,
        )
    }
}
