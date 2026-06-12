package com.feedman.android.shell

/**
 * ドロワーに表示する項目のうち、UI（Compose）から分離してテスト可能にする純粋データ
 * 定義（Issue #29 / Req 2.4, 2.5, 4.1, 4.2, 4.3）。
 *
 * - メイン項目（[DrawerMainItem]）: 「すべての新着」「お気に入り」の 2 件。それぞれ
 *   `timeline` / `starred` ルートにマップされる。
 * - フィード一覧（中段）: #30 の領分のため、本 Issue では静的 placeholder のままで
 *   描画する（テスト対象外）。
 * - フッタ項目（[DrawerFooterItem]）: アカウント / テーマ切替の 2 件。**v1 では
 *   「キーワード通知」項目を含めない**（Req 4.1, 4.2）。
 *
 * `DrawerContent` Composable は本ファイルで宣言した不変リスト（[drawerMainItems] /
 * [drawerFooterItems]）を読み取って描画するため、JVM 単体テストで「v1 のフッタに
 * キーワード通知が含まれないこと」「メイン項目がルートに正しく対応すること」を
 * Compose 起動なしで検証できる（NFR 2.1）。
 */
enum class DrawerMainItem(val routeId: String) {
    /** 「すべての新着」（Req 2.4）。 */
    Timeline(AppRoute.ROUTE_TIMELINE),

    /** 「お気に入り」（Req 2.5）。 */
    Starred(AppRoute.ROUTE_STARRED),
}

/**
 * ドロワーフッタに並べる項目（Req 4.1, 4.2）。
 *
 * **v1 で表示する項目はアカウントとテーマ切替のみ**。`KeywordNotification` を意図的に
 * 列挙に含めないことで、フッタ表示ロジックが本 enum を起点に描画するかぎり Req 4.1
 * / 4.2 を構造的に満たせる。
 */
enum class DrawerFooterItem {
    /** アカウント（ログアウト・退会等のシート起動入口）。 */
    Account,

    /** ライト / ダークモード切替。 */
    ThemeToggle,
}

/**
 * メインナビゲーション項目のリスト（表示順は本リストの順番に従う）。
 */
val drawerMainItems: List<DrawerMainItem> = listOf(
    DrawerMainItem.Timeline,
    DrawerMainItem.Starred,
)

/**
 * フッタ項目のリスト（表示順は本リストの順番に従う）。
 *
 * Req 4.3 の「後続フェーズで通知導線を解禁する際にひとつのスイッチで切り替えられる」
 * 要件は、本リスト生成箇所だけを書き換えれば足りる構造にすることで担保する
 * （`KeywordNotification` を追加するかどうかは本リスト 1 箇所で決まる）。
 */
val drawerFooterItems: List<DrawerFooterItem> = listOf(
    DrawerFooterItem.Account,
    DrawerFooterItem.ThemeToggle,
)

/**
 * メインナビゲーション項目の選択に対応する遷移先ルート ID を返す（Req 2.4, 2.5）。
 *
 * 「すべての新着」「お気に入り」を `timeline` / `starred` ルートにマップする。
 * UI 層（[DrawerContent] / [AppShell]）は当該 ID を `navController.navigate` に渡す。
 */
fun DrawerMainItem.targetRouteId(): String = routeId
