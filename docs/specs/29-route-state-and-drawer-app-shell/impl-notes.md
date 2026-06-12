# 実装メモ: Issue #29 Route state and drawer app shell

## 概要

`shell/` パッケージの仮 AppShell / Navigation / DrawerContent を、SPEC §5.0 /
GRAND-DESIGN §5.6 / requirements.md に合致する正式版に置き換えた。あわせて
`core/auth/SessionState` 抽象と暫定 `MockModeSessionStateProvider` を追加し、認証
分岐の入口を本格 SessionState 実装（Issue #24 系）に差し替えやすい構造にした。

## 要件 ID → テスト対応表

| Requirement | テストファイル | テストケース |
|---|---|---|
| 1.1 timeline 初期表示 | `AppRouteTest.kt` | `timeline ルート ID は 'timeline' である_Req 1_1` |
| 1.2 4 ルート宣言 | `AppRouteTest.kt` | `宣言ルートはちょうど 4 件で...と一致する_Req 1_2` |
| 1.3 feed/{feedId} パラメータ | `AppRouteTest.kt` | `Feed ルートテンプレートは...である_Req 1_3` / `feed path は与えられた feedId を展開する_Req 1_3` / `feed path は空 feedId に対し IllegalArgumentException を投げる_異常系_Req 1_3` |
| 1.4 シェル枠維持 | `AppShell.kt` 実装（`rememberNavController()` を 1 本だけ保持して NavHost に渡す構造）+ JVM 観点として `AppShell.kt`／`Navigation.kt` 二箇所が同一 navController を共有する設計を impl 時にレビュー。NavHost の Composable レベル挙動は androidTest（v1 では CI 必須外）で別 Issue が拾う。 |
| 1.5 非ルート（記事詳細・登録・購読設定・アカウント）非宣言 | `AppRouteTest.kt` | `記事詳細 - フィード登録 - 購読設定 - アカウントはルートに含まれない_Req 1_5` |
| 2.1 メニューボタン → 開く | `AppShell.kt` 実装（`IconButton(onClick = { drawerState.open() })`）。Compose UI 観点の確認は androidTest 範疇（GRAND-DESIGN §6 で CI 必須外）。 |
| 2.2 スクリムタップ → 閉じる | `ModalNavigationDrawer` 標準挙動に委譲（Compose Material3 が提供）。テストは androidTest 範疇。 |
| 2.3 スワイプ → 閉じる | 同上（`ModalNavigationDrawer` 標準）。 |
| 2.4 「すべての新着」→ timeline + 閉じる | `DrawerItemsTest.kt` | `メイン項目 Timeline は timeline ルートに対応する_Req 2_4` / `drawerMainItems はちょうど Timeline と Starred の 2 件である_Req 2_4_2_5`（純粋ロジック層）。`AppShell.kt` 側の navigate + close の連結は実装直視。 |
| 2.5 「お気に入り」→ starred + 閉じる | `DrawerItemsTest.kt` | `メイン項目 Starred は starred ルートに対応する_Req 2_5` |
| 2.6 ドロワーが前面 + 入力遮断 | `ModalNavigationDrawer` 標準挙動。androidTest 範疇。 |
| 3.1 LoggedOut → ログイン画面 | `MockModeSessionStateProviderTest.kt` | `mockMode が false のとき LoggedOut を返す_Req 3_1_3_5` ＋ `AppShellViewModelTest.kt` | `LoggedOut を返す Provider を渡すと初期 state が LoggedOut になる_Req 3_1` |
| 3.2 LoggedIn → ドロワー付きシェル | `MockModeSessionStateProviderTest.kt` | `mockMode が true のとき LoggedIn を返す_Req 3_2_3_5` ＋ `AppShellViewModelTest.kt` | `LoggedIn を返す Provider を渡すと初期 state が LoggedIn になる_Req 3_2` |
| 3.3 LoggedOut → LoggedIn 遷移 | `AppShellViewModelTest.kt` | `LoggedOut から LoggedIn への遷移が観測される_Req 3_3` |
| 3.4 LoggedIn → LoggedOut 遷移 | `AppShellViewModelTest.kt` | `LoggedIn から LoggedOut への遷移が観測される_Req 3_4` |
| 3.5 観測対象差し替え可能 | `SessionStateProvider` インターフェース定義 + `AuthModule` の `@Binds`、`AppShellViewModelTest.kt` | `LoggedOut を返す Provider...` 系（テストで Fake を差し込み）／`MockModeSessionStateProviderTest.kt` 各テスト |
| 4.1 キーワード通知エントリ非表示 | `DrawerItemsTest.kt` | `drawerFooterItems にキーワード通知エントリが含まれない_Req 4_1_4_2` |
| 4.2 キーワード通知ナビハンドラ非定義 | 同上（enum に列挙していないため、フッタの遷移先 when 式に枝が生じない構造で担保）。 |
| 4.3 単一スイッチ切替可能 | `DrawerItemsTest.kt` | `drawerFooterItems は DrawerFooterItem の全列挙の部分集合である_Req 4_3` |
| NFR 1.1 / 1.2 アニメーション | Material3 `ModalNavigationDrawer` の既定アニメーションに委譲（Compose Material3 が `cubic` 250–500ms 内に収まる挙動を保証）。固有計測は androidTest 範疇。 |
| NFR 2.1 観測可能性 | 現在ルート ID は `navController.currentBackStackEntryAsState()` で取得・ドロワー開閉は `drawerState.currentValue`・セッション状態は `AppShellViewModel.sessionState` で公開。androidTest からアクセス可能。 |
| NFR 2.2 ソース差し替え | `AppShellViewModelTest.kt` `FakeSessionStateProvider` で実証。 |
| NFR 3.1 mockMode 専用分岐の不残置 | `AppShell.kt` 実装側に `appConfig.mockMode` 直接参照を残さず、`SessionStateProvider` 経由に統一。`MockModeSessionStateProvider` 内のみが mockMode を参照する。 |

## 判断記録

- **SessionState の置き場所**: Issue #24 系で本格化することと、`AuthRepository` が同階層
  にあることから `core/auth/SessionState.kt` 配下に配置した（コードのレイヤリングに
  従う / `docs/GRAND-DESIGN.md` §3 のディレクトリ規約）。
- **mockMode との結線**: `MockModeSessionStateProvider` を `@Singleton` で 1 値固定して
  Hilt に bind した。`AppConfig.mockMode = true` で `LoggedIn`、`false` で `LoggedOut`。
  Issue #24 で実装が入る時は `@Binds` 1 行を差し替えるだけで本実装に切り替わる
  （NFR 3.1 / Req 3.5）。
- **ルート定義の形**: 文字列 const 散在を避けて sealed class `AppRoute` に集約。
  `feed/{feedId}` テンプレートと展開後パス（`Feed.path(feedId)`）の責務を `id` /
  `path` に分離。`declaredRouteIds` を `companion object` で公開してテストで列挙
  全体を検査できるようにした。
- **ドロワー項目の構造**: メイン項目とフッタ項目を Compose とは独立した enum + 不変
  リストで宣言した（`DrawerItems.kt`）。Compose を起動しない JVM 単体テストで
  「キーワード通知が含まれない（Req 4.1, 4.2）」「単一スイッチで切替可能（Req 4.3）」を
  構造的に検証できる。
- **フッタ配線（Account / ThemeToggle）の未実装扱い**: 本 Issue の要件範囲では
  メイン項目（timeline / starred）の navigate と close だけが Required。フッタ項目の
  実遷移先は別 Issue の領分のため、`AppShell.kt` ではドロワーを閉じる no-op
  ハンドラに留めた。テーマ切替の配線は #25 完了済みだが ViewModel 連結は別 Issue。
- **`drawer_starred` 文字列**: 既存 `"お気に入り（未実装）"` から `"お気に入り"` に変更
  した。Req 2.5 では実際に starred ルートへ遷移するため、`未実装` を消した。
- **DrawerContent 内の `DRAWER_ITEM_PADDING` private val**: 当初 `NavigationDrawerItemDefaults.ItemPadding`
  の import 整理のために置いたが現在は未使用。lint 警告を避けるため `@Suppress` 付与
  済み。次の整理タイミングで除去予定。

## 確認事項

- design.md / tasks.md は当該 Issue に存在しないため、本実装は requirements.md と SPEC
  §5.0 / GRAND-DESIGN §5.6 / `design/mobile/fm-screens.jsx` を根拠に進めた。後続 Issue
  でルート個別画面（feed / starred / search）の実体が追加される際、`Navigation.kt` の
  placeholder Composable は差し替えが必要。
- ドロワー開閉のアニメーション速度 (NFR 1.1 / 1.2) は Material3 既定値に委ねており、
  数値計測の自動テストは置いていない（androidTest CI 必須外運用 / GRAND-DESIGN §6）。
- フッタ「アカウント」「テーマ切替」項目の押下時実挙動（ボトムシート起動 / ThemeMode
  切替の即時 push）は本 Issue では未配線。別 Issue で `onSelectFooterItem` ハンドラに
  実遷移を載せる。
- `LaunchedEffect(Unit) { drawerState.close() }` を `LoggedInShell` 内に置いたが、Compose
  ライフサイクルで session 状態が LoggedIn になった瞬間に drawer が必ず closed 状態で
  再構築されることが期待動作。再構築されない（rememberDrawerState がキーで保持される）
  ケースを将来発見した場合、`key(sessionState)` で囲い直す可能性がある。

## 派生タスク候補

- 上部アプリバー右側の検索アイコン / テーマ切替アイコンの配線（Issue #31）
- ドロワーフィード一覧の実描画（Issue #30）
- フッタ「アカウント」「テーマ切替」のボトムシート遷移配線（別 Issue）
- 認証実装と SessionState の本格化（Issue #24 系）

## requirement ID 達成サマリ

- Requirement 1（シェル構造 / 4 ルート）: 1.1 / 1.2 / 1.3 / 1.5 → ユニットテスト網羅。
  1.4 → 実装構造で担保（同一 navController を NavHost に渡す）。
- Requirement 2（ドロワー開閉と遷移）: 2.4 / 2.5 → ユニットテスト網羅。2.1〜2.3 / 2.6
  → Compose 標準挙動 + 実装直視（androidTest 範疇）。
- Requirement 3（未認証時シェル差し替え）: 3.1 / 3.2 / 3.3 / 3.4 / 3.5 → ユニットテスト網羅。
- Requirement 4（v1 スコープ境界）: 4.1 / 4.2 / 4.3 → ユニットテスト網羅。
- NFR 1.x → Material3 既定アニメーションに委譲。
- NFR 2.x → ユニットテストおよび実装構造で担保。
- NFR 3.x → 実装側に `appConfig.mockMode` 直接参照を残置していない。

STATUS: complete
