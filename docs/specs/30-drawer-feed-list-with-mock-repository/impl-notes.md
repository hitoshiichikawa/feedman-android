# 実装ノート: Issue #30 Drawer feed list with mock repository

## 実装サマリ

ナビゲーションドロワーのフィード一覧を、実 API 統合（#39）を待たずに先行実装した。
購読フィードのデータソース抽象 `SubscriptionRepository` と Fake 実装を `core/data` / `core/data/fake`
に追加し、`DrawerViewModel` で `StateFlow<DrawerUiState>` を公開する。`DrawerContent` Composable は
#29 のプレースホルダを差し替えて、Favicon + タイトル + 状態アイコン + 未読バッジ + 設定アイコンの
行を描画する。AppShell から `feed/{feedId}` への navigate / ドロワークローズを配線済み。

## requirement ID → テスト対応表

| Req ID | テスト |
|---|---|
| 1.1 (ドロワー開→一覧表示) | `DrawerViewModelTest.uiState はリポジトリの順序のまま行へ変換する_Req 1_5` / `FakeSubscriptionRepositoryTest.observeSubscriptions は購読開始時点で即座にリストを 1 度 emit する_Req 5_3`（リポジトリ即時 emit 〜 ViewModel 反映の経路を覆う） |
| 1.2 (favicon + タイトル + 未読 + 状態 + 設定の順序) | `DrawerFeedRowTest.Subscription から DrawerFeedRow への変換が必要なフィールドを正しく拾う_Req 1_2_3_3_5_4`（行構成要素の生成）。Composable 配置自体は `DrawerContent.kt` の Row 並び順で保証（NFR 1.1）|
| 1.3 (空リスト → 行 0 件) | `DrawerViewModelTest.リポジトリが空のとき uiState_rows も空_Req 1_3` |
| 1.4 (タイトル省略表記 1 行) | Composable 側で `maxLines = 1` + `TextOverflow.Ellipsis` を直接適用（純粋ロジックの分岐ではないため JVM テスト対象外。NFR 1.2 と同じ） |
| 1.5 (順序保持) | `DrawerViewModelTest.uiState はリポジトリの順序のまま行へ変換する_Req 1_5` / `FakeSubscriptionRepositoryTest.複数回購読しても同じ順序のスナップショットが返る_Req 1_5` |
| 1.1.1 (未読 ≥1 でバッジ表示) | `DrawerFeedRowTest.unread が 1 以上のときは未読バッジを表示する_Req 1_1_1` / `Subscription から DrawerFeedRow への変換が必要なフィールドを正しく拾う_Req 1_2_3_3_5_4` |
| 1.1.2 (未読 0 でバッジ非表示) | `DrawerFeedRowTest.unread が 0 のときは未読バッジを非表示にする_Req 1_1_2` / `Subscription から DrawerFeedRow への変換で unread 0 のときは showUnreadBadge が false_Req 1_1_2` + 負値境界 `unread が負の値（境界値）でも未読バッジを表示しない` |
| 2.1 (stopped → pause) | `DrawerFeedRowTest.feed_status stopped のとき停止アイコンを選択する_Req 2_1` |
| 2.2 (error → alert 危険色) | `DrawerFeedRowTest.feed_status error のとき警告アイコンを選択する_Req 2_2`。色 `colorScheme.error` 適用は Composable 側 |
| 2.3 (active → アイコン非表示) | `DrawerFeedRowTest.feed_status active のとき状態アイコンを表示しない_Req 2_3` + 未知値フォールバック `未知の feed_status は安全側で None にフォールバックする` |
| 2.4 (状態アイコンを未読バッジの左に配置) | `DrawerContent.kt` 内 Row の並び順で構造的に保証（純粋ロジック分岐ではない） |
| 3.1 (行タップ → feed/{feedId}) | `DrawerFeedRowTest.Subscription から DrawerFeedRow への変換が必要なフィールドを正しく拾う_Req 1_2_3_3_5_4`（feedId 抽出を担保）+ `AppShell.kt` の `navController.navigate(AppRoute.Feed.path(row.feedId))` 配線 |
| 3.2 (行タップ → ドロワー閉) | `AppShell.kt` の `coroutineScope.launch { drawerState.close() }` 配線 |
| 3.3 (遷移先 feedId は repository から) | `DrawerFeedRowTest.Subscription から DrawerFeedRow への変換が必要なフィールドを正しく拾う_Req 1_2_3_3_5_4` |
| 4.1 (設定アイコン → コールバック発火) | `DrawerContent.kt` の `IconButton(onClick = { onSelectFeedSettings(row) })` 配線。AppShell から no-op を注入。本 Issue では UI 配線レベルで担保 |
| 4.2 (行遷移と設定タップを同時に発火させない) | `IconButton` を Row の clickable と分離した独立 click target にすることで構造的に保証 |
| 4.3 (設定タップでドロワー閉じない) | AppShell の `onSelectFeedSettings` 実装が `drawerState.close()` を呼ばない（no-op） |
| 5.1 (Repository が Flow 公開) | `FakeSubscriptionRepositoryTest.observeSubscriptions は購読開始時点で即座にリストを 1 度 emit する_Req 5_3` |
| 5.2 (active/stopped/error をサンプルに含む) | `FakeSubscriptionRepositoryTest.モックデータに active stopped error の状態がそれぞれ 1 件以上含まれる_Req 5_2` |
| 5.3 (購読開始で即座に emit) | `FakeSubscriptionRepositoryTest.observeSubscriptions は購読開始時点で即座にリストを 1 度 emit する_Req 5_3` |
| 5.4 (feed_id / title / favicon / unread / status を含む) | `FakeSubscriptionRepositoryTest.モックデータに favicon data URL と null の両方が含まれる_Req 5_4`（favicon カバレッジ）+ `DrawerFeedRowTest.Subscription から DrawerFeedRow への変換が必要なフィールドを正しく拾う_Req 1_2_3_3_5_4`（必須フィールド抽出） |
| NFR 1.1 (fm-screens.jsx 準拠) | `DrawerContent.kt` の Row 並び順 + design/mobile/fm-screens.jsx FMFeedListBody 比較（人間レビュー） |
| NFR 1.2 (1 行省略) | `maxLines = 1` + `TextOverflow.Ellipsis`（Composable 直適用） |
| NFR 1.3 (文字列リソース化) | `strings.xml` に drawer_feed_* 追加（NFR 1.3 の構造担保） |
| NFR 2.1 (新リストの即時反映) | `DrawerViewModelTest.リポジトリが新しいリストを emit すると uiState に反映される_NFR 2_1` |
| NFR 3.1 (テストで Fake 差替) | `FakeSubscriptionRepositoryTest` 全体 / `DrawerViewModelTest` の `StubSubscriptionRepository` 注入 |
| NFR 3.2 (UI テストでコールバック検証可能) | `DrawerContentStateless` を internal 抽出 + コールバック引数化。実 UI テスト追加は #43 以降に委ねる |

## 判断記録

- **状態アイコンの fallback**: `FeedStatusIcon.from` で未知値 → `None` にフォールバックさせた。
  SPEC §4.2 では active / stopped / error の 3 値だが、API 不整合時に UI がクラッシュしないように
  防衛的に処理する判断。テストで明示。
- **未読バッジを Material3 `Badge` ではなく自作 `Box`**: `Badge` は通常 `BadgedBox` 内のアンカー扱いで、
  ドロワー行のような並列配置だと位置調整が煩雑なため、シンプルな Pill 形 `Box` を採用。
- **設定アイコンを `IconButton` で独立クリック target に**: Row の `clickable` と分離することで
  Req 4.2「行タップと同時に発火させない」を構造的に保証。`stopPropagation` 的な明示制御は不要。
- **Favicon サイズ**: `feedmanDimens.faviconMedium`（28dp）を採用。プロト `FMFeedListBody` の `size={28}` に
  整合（NFR 1.1）。
- **`DrawerContentStateless` の internal 抽出**: NFR 3.2 を見据えて Composable を stateless 分離。
  ViewModel に依存しない呼び出しを可能にしている（本 Issue では Compose UI Test は追加しない）。

## 確認事項（レビュワー判断ポイント）

- v1 のドロワー視覚仕様について、`design/mobile/fm-screens.jsx` の FMFeedListBody との細部差分
  （アクセントカラーの選択、列幅 30dp vs 28dp など）は Compose 標準コンポーネントの制約で
  ピクセル一致は狙わず、相対配置・順序・要素の有無で整合させた（NFR 1.1）。
- ドロワーヘッダー左の navItem「すべての新着 / お気に入り」内に表示される未読合計バッジ
  （プロトの `total = data.feeds.reduce((s, f) => s + f.unread_count, 0)`）は本 Issue 要件外と判断し
  未実装。要件 1.1〜1.5 はあくまでフィード「行」を対象としており、メイン項目側の集計バッジは
  Out of Scope の項目「フィード行の長押しメニュー・並び替え・スワイプ操作」と並ぶ視覚拡張として、
  別 Issue で扱う想定。設計者が違う見解の場合は指摘してほしい。
- 設定アイコンタップ時のコールバックは本 Issue では no-op。#43 で実シートを実装する際に
  `AppShell` の `onSelectFeedSettings = { /* 開く */ }` 部分を実装することになる。
- Compose UI Test（行タップでコールバックが呼ばれる / バッジ表示有無 等）は本 Issue では追加していない。
  NFR 3.2 の「外部から注入可能」構造は満たしているため、UI テストの正式追加は別 Issue で良いと判断。

## 派生タスク候補

- ドロワー上部の「すべての新着 / お気に入り」の未読合計バッジ表示（プロトの `FMFeedListBody` 上段）
- フィード行の長押しメニュー（Out of Scope 明示済み）
- 並び替え（アルファベット順 / カスタム）

STATUS: complete
