# Issue #34 実装ノート — Timeline refresh and infinite scroll states

## 概要

横断タイムライン画面に pull-to-refresh と無限スクロールに伴う状態表示
（初回ロード／空／追加読込／追加エラー／終端／refresh エラー通知）を結線した。
カード描画（#33）・状態表示プリミティブ（#28）・CrossFeedRepository（#32）の上に、
状態解決の純粋ロジックと Composable 配線を最小差分で追加している。

## 実装方針

### 状態解決の純粋関数化

画面全体状態（4 状態）の判定を `resolveTimelineScreenState(refresh, itemCount)` として
`core/ui/ListFooterState.kt` に共置で追加した。Composable / Paging の Composable 描画から
切り離し、JVM 単体テストで refresh × itemCount の全組合せを検証している。

判定優先順位:

1. `itemCount > 0` のとき常に `Content`（Req 5.3 / 4.2: 既存一覧を破壊しない）
2. `itemCount == 0` のとき refresh の `LoadState` に従う
   - `Loading` → `InitialLoading`
   - `Error` → `InitialError`
   - `NotLoading` → `Empty`

フッタ状態は既存の `resolveListFooterState`（#28）をそのまま再利用した。優先順位は
Loading > Error > EndOfList > None で、Content 表示中のみ呼ばれる。

### pull-to-refresh

Material 3 `PullToRefreshBox`（Compose BOM 2024.10.01 / material3 1.3.1）を採用した。
`onRefresh` で `LazyPagingItems.refresh()` を呼ぶと Pager が `PagingSource` を再生成し、
`CrossFeedRepositoryImpl` 側で `sessionSinceTime` が破棄される。新初回レスポンスで
再固定される動線は #32 の規約として既に成立しているため、`TimelineScreen` 側では
`refresh()` の呼び出しのみで Req 1.1 / 1.4 が満たされる。

per-feed fetch (`POST /api/feeds/{id}/fetch`) は呼ばない（Req 1.5 / SPEC §4.2 注意）。
`CrossFeedRepository` 経由でしか API を叩かないため、コード上で構造的に担保される。

### refresh エラーの snackbar 通知

`LaunchedEffect(refresh, items.itemCount)` で「直前が Loading だったか」（`wasRefreshing`）
を `rememberSaveable` に保持し、Loading → Error の遷移を検出して `FeedmanSnackbar.show` を
発火する。条件: `wasRefreshing && refresh is Error && itemCount > 0`。

- `itemCount == 0` の初回エラーは画面全体 `ErrorFullScreen` で表示するため snackbar 対象外
- 既存一覧と since_time は CrossFeedRepository 側で `LoadResult.Error` 時に破棄されないため、
  Req 5.3 は構造的に担保される（UI 側で何もしないことが正しい挙動）

### LazyColumn フッタの排他挿入

Content 状態のとき、`resolveListFooterState` の戻り値で末尾に LoadingFooter / ErrorFooter /
EndOfListFooter を `item(key = ...)` で挿入する。終端メッセージ表示中は Paging 3 の規約により
`endOfPaginationReached=true` のとき append 要求が出ないため、Req 2.5 は Paging 側で担保される。

## requirement ID → テスト対応表

| Requirement | テスト / 担保場所 |
|---|---|
| 1.1 onRefresh で先頭再取得 | `TimelineScreen.kt` `PullToRefreshBox(onRefresh = { items.refresh() })` |
| 1.2 進行中の indicator | `PullToRefreshBox(isRefreshing = refresh is Loading && itemCount > 0)` |
| 1.3 完了時に indicator 消去 | `isRefreshing` が NotLoading で false に戻る Compose 規約 |
| 1.4 since_time 再固定 | `CrossFeedRepositoryImpl.newPagingSource()` が PagingSource 再生成時に sessionSinceTime をリセット（#32 既存規約） |
| 1.5 per-feed fetch 禁止 | TimelineScreen は CrossFeedRepository 以外を呼ばない（構造的担保） |
| 2.1 末尾で自動次ページ | Paging 3 / LazyPagingItems の規約（CursorPagingSource の append load） |
| 2.2 フッタ Loading 表示 | `TimelineScreenStateTest` + `ListFooterStateTest` の Loading ケース／TimelineScreen の LoadingFooter 挿入 |
| 2.3 成功時 Loading 消去 | `resolveListFooterState` が None を返す（既存 ListFooterStateTest） |
| 2.4 終端メッセージ | `TimelineScreen` で `append is NotLoading && endOfPaginationReached` を EndOfListFooter に変換 |
| 2.5 終端時に追加要求しない | Paging 3 規約（endOfPaginationReached=true で append が走らない） |
| 3.1 初回 LoadingFullScreen | `TimelineScreenStateTest.Req 3_1 refresh loading and item count zero returns InitialLoading` |
| 3.2 初回 ErrorFullScreen + 再試行 | `TimelineScreenStateTest.Req 3_2 refresh error and item count zero returns InitialError` ＋ TimelineScreen の `ErrorFullScreen(onRetry = items::retry)` |
| 3.3 再試行で先頭再取得 | `LazyPagingItems.retry()` の規約（refresh エラーは refresh 再起動） |
| 3.4 1 件以上で表示 | `TimelineScreenStateTest.boundary item count one returns Content regardless of refresh state` |
| 4.1 フッタ Error + 再試行 | `TimelineScreen` の `ErrorFooter(onRetry = items::retry)` 挿入／`ListFooterStateTest` Error ケース |
| 4.2 既存一覧を保持 | `TimelineScreenStateTest.Req 5_3 refresh error with positive item count keeps Content`（同質ケースとして担保） |
| 4.3 失敗ページ再読込 | `LazyPagingItems.retry()` の規約 |
| 4.4 成功でフッタ消去 | `resolveListFooterState` が None を返す（既存 ListFooterStateTest） |
| 5.1 refresh 失敗で indicator 終了 | refresh が Error になると `isRefreshing = false` になり PullToRefreshBox の indicator が消える |
| 5.2 失敗をユーザー可視通知 | `TimelineScreen` の `LaunchedEffect` で FeedmanSnackbar.show 呼び出し |
| 5.3 一覧と since_time を保持 | `TimelineScreenStateTest.Req 5_3 refresh error with positive item count keeps Content` ＋ CrossFeedRepository 側の規約（#32 Req 5.3） |
| 6.1 空状態表示 | `TimelineScreenStateTest.Req 6_1 refresh not loading and item count zero returns Empty`（2 件） |
| 6.2 空状態で pull-to-refresh 受付 | `TimelineScreen` で Empty も `PullToRefreshBox` の内側に描画 |
| 6.3 取得後に空状態解除 | `resolveTimelineScreenState` が itemCount > 0 で Content を返す（同上テストでカバー） |
| NFR 1.1 / 1.2 API 利用境界 | TimelineScreen は CrossFeedRepository 以外を呼ばない |
| NFR 2.1 画面全体状態の一意性 | `TimelineScreenState` sealed interface ＋ when 分岐 |
| NFR 2.2 視覚区別 | `PullToRefreshBox` の上部 indicator と `LoadingFooter` の末尾 indicator は別 Composable |
| NFR 3.1 / 3.2 共通部品との一貫性 | `LoadingFullScreen` / `ErrorFullScreen` / `EmptyState` / `LoadingFooter` / `ErrorFooter` / `EndOfListFooter`（#28）をそのまま利用 |

## 判断記録

- **PullToRefreshBox vs PullRefresh state + Modifier**: 採用案は `PullToRefreshBox`。
  material3 1.3.1 で公式安定 API として提供されており、内部状態（indicator alpha / arrow）も
  デフォルトで適切。`pullToRefreshIndicator` を Modifier 直当てする低レベル API もあるが、
  本要件では indicator のカスタマイズが不要なため shorthand を採用。
- **snackbar 配置スコープ**: AppShell に共通 SnackbarHost を置く案も検討したが、
  Issue #34 のスコープが「タイムライン画面」に限定されており、他画面（フィード別 / スター /
  検索）の snackbar 仕様は別 Issue（#38 楽観的更新 など）で結線される。本 Issue では
  TimelineScreen 内に閉じた SnackbarHostState を持たせ、Box の BottomCenter に SnackbarHost を
  配置する形でスコープを限定した。
- **画面全体状態の itemCount 優先順位**: refresh=Loading + itemCount > 0 のとき、
  画面全体 LoadingFullScreen ではなく Content を返す設計とした（PullToRefreshBox の
  indicator が refresh 表示を担うため、NFR 2.1 の同時提示 1 つ制約と整合）。Req 5.3
  「既存一覧を保持」と同じ原則。
- **フッタ判定での endOfPaginationReached**: `append is NotLoading && append.endOfPaginationReached`
  を終端条件にしている。`refresh is NotLoading && endOfPaginationReached` ではなく
  append 側で判定するのは Paging 3 の規約（append の NotLoading が末尾ページ確定の信号）に
  従ったため。CursorPagingSource（#18）が `nextKey == null` で末尾を返す挙動と一致。
- **rememberSaveable for wasRefreshing**: 画面回転や再コンポジションで状態が
  巻き戻ると refresh 通知が二重発火する恐れがあるため `rememberSaveable` を採用。
  Boolean なので Saveable は自動対応。

## 確認事項（レビュワー判断ポイント）

- requirements.md の Req 5.2「再取得失敗をユーザー可視のメッセージとして通知」の
  実装手段として `FeedmanSnackbar.show(...)` を採用しているが、要件側は「メッセージ通知」と
  しか書かれていない。snackbar 以外（toast / 永続バナー / dialog 等）が望ましい場合は
  PM / Architect への差し戻しが必要。
- AppShell に共通 SnackbarHost を置かず TimelineScreen 内に SnackbarHostState を
  持たせている。後続 Issue で snackbar 通知が増える場合、AppShell に一段上げて共通化する
  リファクタが必要になる可能性がある（本 Issue のスコープ外）。
- `rememberPullToRefreshState()` は `ExperimentalMaterial3Api` opt-in を要求する。
  material3 1.3.1 では `PullToRefresh*` 系の多くが experimental 印付きだが、
  API は安定リリース版に同梱されており、Compose BOM 2024.10.01 で利用可能。

## 補足: 派生タスク候補

- AppShell に共通 SnackbarHost を集約する refactor（#38 着手前に行うのが望ましい）
- Compose UI Test での pull-to-refresh / フッタ表示の instrumented 検証
  （Issue #34 ではスコープ外。SPEC §10 の受け入れ基準に組み込む別 Issue）
- フィード別画面（§5.2）の pull-to-refresh とクールダウン案内（要件で本 Issue から除外済み）

## ビルド結果

`./gradlew build` 成功（lint / unit test を含む）。

STATUS: complete
