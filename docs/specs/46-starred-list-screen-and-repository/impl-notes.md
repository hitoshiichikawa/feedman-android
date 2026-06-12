# 実装ノート — Issue #46 スター一覧画面 / リポジトリ

本 Issue (#46) の実装にあたって行った判断・確認事項と、requirements.md の各 numeric ID に
対応するテストケースを記録する。

## requirement ID → テスト対応表

| Req ID | 要件 | 主なテスト |
|---|---|---|
| 1.1 | ドロワー「お気に入り」エントリ → スター一覧画面遷移 | `StarredViewModelTest` 全体（VM が hiltViewModel で起動可能なこと）/ `shell/Navigation.kt` の手動結線（既存 AppShellTest で網羅済みのドロワー導線挙動を流用） |
| 1.2 | 画面ヘッダのタイトル表示 | 既存の `AppBarTitleResolverTest`（`appbar_title_starred` = 「お気に入り」を `resolveAppBarTitle` が返す）+ `strings.xml` の `appbar_title_starred` 値で担保 |
| 1.3 | スクロール可能な縦リスト表示 | `StarredViewModelTest.Req 1-3 1-4 cardPagingData は items を ArticleCardModel に変換し feed_title をソース表示に伝える` |
| 1.4 | feed_title をソース表示として行内に提示 | `StarredCardModelMapperTest.feed_title はカードの feedTitle にそのまま転写される` + `StarredViewModelTest.Req 1-3 1-4 ...` |
| 2.1 | 初回は cursor 未指定で先頭ページ取得 | `StarredItemsRepositoryImplTest.Req 2-1 initial load issues GET with no cursor query` |
| 2.2 | 後続ページ要求で次カーソルを搬送 | `StarredItemsRepositoryImplTest.Req 2-2 subsequent load forwards previous next_cursor as cursor query` |
| 2.3 | 終端をページング状態に反映 | `StarredItemsRepositoryImplTest.Req 2-3 has_more false on response terminates paging with null nextKey` |
| 2.4 | 終端到達後は追加要求を発行しない | `StarredItemsRepositoryImplTest.Req 2-4 no further request is issued after terminal reached via TestPager` |
| 2.5 | 初回失敗をページング状態のエラーとして露出 | `StarredItemsRepositoryImplTest.Req 2-5 initial load failure surfaces as LoadResult Error ...` + `Req 2-5 network failure surfaces FeedmanException with NETWORK_ERROR code` |
| 2.6 | 追加ロード失敗で既読み込み済みページを破棄しない | `StarredItemsRepositoryImplTest.Req 2-6 subsequent load failure surfaces error without discarding previous page` |
| 3.1 | 0 件読み込み完了時に空状態表示 | `StarredViewModelTest.空のスター一覧も PagingData として伝播する_Req 3_1 の前提` + `StarredScreen` の `EmptyState` 分岐（共有プリミティブ） |
| 3.2 | Pull-to-refresh で先頭ページから再取得 | `StarredItemsRepositoryImplTest.Req 3-2 refresh issues new request from head with cursor unset` |
| 3.3 | リフレッシュ後の終端判定は Req 2 と同規則 | `StarredItemsRepositoryImplTest.Req 3-2 ...` の後段アサート（`refreshedResult.nextKey == null`） |
| 3.4 | リフレッシュ失敗のユーザー通知 | `StarredItemsRepositoryImplTest.Req 3-4 refresh failure surfaces as LoadResult Error` + `StarredScreen` の snackbar 発火ロジック（`refresh is LoadState.Error && itemCount > 0` のトランジション検出） |
| 4.1 | 行タップで記事詳細シートを開く | `StarredScreen` の `onOpenItemDetail` 受け渡し + `shell/Navigation.kt` 結線（AppShell 直下の `articleDetailViewModel.open(itemId)` へ伝達） |
| 4.2 | 記事詳細シートが既存挙動を提供 | 既存 `ArticleDetailViewModelTest` で網羅。本 Issue は詳細シート自体を改修しない（Out of Scope） |
| 5.1 | スターアイコンのトグルが overlay 経由で即時更新 | `StarredViewModelTest.Req 5-1 ItemStateStore overlay 値はサーバー値より優先される（即時反映）` + `toggleStar で ItemStateStore_setStarred 経由のサーバー反映が走る` |
| 5.2 | 詳細シート由来の overlay 更新も同期 | `StarredViewModelTest.Req 5-2 詳細シート由来の overlay 更新も同じストアを経由して即時反映される`（ItemStateStore は singleton で全画面共通） |
| 5.3 | スター解除時の残置（リストから除去せず outline 化） | `StarredViewModelTest.Req 5-3 スター解除（overlay isStarred=false）でも当該行はリストから除去されない` |
| 5.4 | リフレッシュ / 再入場で解除済みを除外 | `StarredViewModelTest.Req 5-4 リフレッシュ後にサーバーが解除済みを除外したレスポンスを返せば当該行は表示されない`（サーバー側 filter で自動成立） |
| 5.5 | ロールバック時の表示復元 | `StarredViewModelTest.Req 5-5 楽観的更新のサーバー反映が失敗するとロールバックで isStarred が直前値に戻る` |
| 6.1 | 変更範囲を feature/starred と core/data に限定 | git diff の対象範囲（feature/starred + core/data + di + shell 結線 + strings.xml + tests）で自己確認 |
| 6.2 | キーワード検索 UI を含まない | StarredScreen に検索 UI を実装していない（プロト FMStarredScreen も同様） |
| 6.3 | 既読／未読フィルタ・ソート切替 UI を含まない | StarredScreen に FilterTabs を実装していない |
| NFR 1.1 | 100 ms 以内のスターアイコン表示更新 | `ItemStateStore` の overlay は MutableStateFlow 即時更新で、Compose の再合成は次フレームで発火する。明示的な遅延は無く、JVM 単体テストでは同期的に observable（`StarredViewModelTest` の snapshot 直後検証で担保） |
| NFR 1.2 | 追加ページ読込中のスクロール阻害なし | Paging 3 の `LazyPagingItems` 規約により append は別 coroutine で実行され、メイン UI スレッドをブロックしない（フレームワーク既定挙動） |
| NFR 2.1 | Repository 単体テストの観点網羅 | `StarredItemsRepositoryImplTest` で正常系 / 終端 / 初回失敗 / 追加失敗 / リフレッシュ成功 / リフレッシュ失敗の 6 観点を網羅 |
| NFR 2.2 | スター解除残置 / リフレッシュ後除去 / ロールバック | `StarredViewModelTest.Req 5-3 / Req 5-4 / Req 5-5` で各 1 ケース以上 |
| NFR 2.3 | feed_title が呼び出し元へ伝達される | `StarredItemsRepositoryImplTest.NFR 2-3 response feed_title is propagated to caller verbatim` + `StarredCardModelMapperTest.feed_title はカードの feedTitle にそのまま転写される` |

## 判断記録

### 1. スター解除時の残置メカニズム（Req 5.3 / 5.4）

requirements.md の Introduction で確定済みの「セッション中はリスト上に残置 + outline 化、
リフレッシュ / 再入場で除去」を以下の最小構造で実装した:

- **残置**: StarredViewModel は overlay の `isStarred=false` を **そのまま** ArticleCardModel
  に反映するだけ。リストから除去するフィルタロジックは **実装しない**。サーバーから返って
  きた starred 記事は全件カードとして描画され続け、StarToggle が outline 状態に切り替わる。
- **除去**: Pull-to-refresh → `LazyPagingItems.refresh()` → Pager invalidate → 新 PagingSource
  が先頭ページから取得し直す。サーバーは `is_starred=true` の記事のみを返すため、解除確定
  済みの記事はレスポンスに含まれない。VM 側で「解除確定済み itemId 集合」を覚える必要は
  ない（NFR 1.1 の責務最小化）。
- **再入場**: ViewModel は NavBackStackEntry スコープで再生成されるため、`cardPagingData`
  Flow も新しい Pager に張り直され、結果的にリフレッシュと同様の経路で除去が成立する。

この方針は cursor 連続性を崩さず（ページ穴が出ない）、ItemStateStore の合成ルール（Issue #38
Req 3）と矛盾しない。代替案として「セッションごとに `isStarred=false` overlay に立った
itemId を覚えてフィルタ除去」も検討したが、(a) 再入場との挙動差が出ること、(b) 楽観的更新
失敗ロールバック時にフィルタ集合を巻き戻す責務が増えること、を理由に採用しなかった。

### 2. ArticleCard の onOpenLink を null にしてカード上の外部リンクアイコンを描画しない

プロト `design/mobile/fm-screens.jsx` `FMStarredScreen` ではカード上に外部リンクアイコンを
表示しておらず、外部リンクへの導線は記事詳細シート経由（Req 4.1）に集約される設計。
StarredScreen でも同方針を採用し、`ArticleCard` の `onOpenLink = null` を明示することで
アイコン非表示にした（既存 ArticleCard の後方互換引数で対応）。`onOpenExternalLink` を
受け取る引数を StarredScreen に持たせる必要はないため、Navigation 結線も `onOpenItemDetail`
のみで足りる。

### 3. リフレッシュ失敗通知を ViewModel ではなく Screen 側で発火する

TimelineScreen と同じく、refresh 失敗の検出は `LazyPagingItems.loadState.refresh` の
`Loading → Error` トランジションを `rememberSaveable` で観測し、`itemCount > 0` のときのみ
snackbar を出す方針にした（初回失敗は ErrorFullScreen で表示するため重複させない）。
ViewModel 側に専用 SharedFlow を用意しない（YAGNI / Req 3.4 の責務を画面側に集約）。

### 4. 1 PR = 1 Issue + 設計ドキュメント不在の補完判断

design.md / tasks.md は不在のため、Developer プロンプトの指示と requirements.md の Req 5
の判断ポイント、および既存実装パターン（TimelineViewModel / FeedScreenViewModel）の流儀を
踏襲した。File Structure Plan として:

- `app/src/main/kotlin/com/feedman/android/core/data/StarredItemsRepository.kt`（interface）
- `app/src/main/kotlin/com/feedman/android/core/data/StarredItemsRepositoryImpl.kt`（impl）
- `app/src/main/kotlin/com/feedman/android/feature/starred/StarredCardModelMapper.kt`
- `app/src/main/kotlin/com/feedman/android/feature/starred/StarredViewModel.kt`
- `app/src/main/kotlin/com/feedman/android/feature/starred/StarredScreen.kt`
- `app/src/main/kotlin/com/feedman/android/di/RepositoryModule.kt`（@Binds 追加）
- `app/src/main/kotlin/com/feedman/android/shell/Navigation.kt`（placeholder 置換）
- `app/src/main/res/values/strings.xml`（空状態 / リフレッシュ失敗文言）
- 各 *.kt に対応する `app/src/test/kotlin/.../StarredItemsRepositoryImplTest.kt` /
  `StarredCardModelMapperTest.kt` / `StarredViewModelTest.kt`
- fixture: `app/src/test/resources/fixtures/starred_page_*.json` 4 種

## 確認事項（レビュワー向け）

- **design.md / tasks.md 不在**: 本 Issue は Architect 起動を経由せず PM の requirements.md
  と既存実装パターン（#33 / #40）を参考に Developer が直接実装した。設計上の「正本」は
  本 impl-notes.md と requirements.md の組み合わせで成立しているが、将来同類機能（#47 検索画面）
  を実装する際に Architect 経由で正式な design.md を起こす方が望ましい。
- **`ArticleCardModel` の後方互換性**: `onOpenLink = null` でアイコン非表示にできることを
  既存実装が前提としているため変更不要（`ArticleCard.kt` 既存 KDoc にも明記済み）。
- **`ItemStateStore` 共有**: スター一覧と詳細シート間の同期は ItemStateStore の singleton 性
  で自動的に成立する（テストでも同じ store インスタンス経由で検証）。
- **Compose UI テスト**: 本 Issue では JVM 単体テストのみで Composable 自体の動作は検証
  していない（CLAUDE.md の指針: 通常 Issue では JVM 単体テストを最優先 / instrumented を
  CI 必須にしない）。StarredScreen の `EmptyState` 分岐は共有プリミティブ (`EmptyState`
  `LoadingFullScreen` `ErrorFullScreen`) を組み合わせているのみで、組み合わせ条件
  （`resolveTimelineScreenState`）は既存 `TimelineScreenStateTest` で網羅されている。

## ビルド / テスト結果

- `./gradlew build` 成功（lint / test / assemble 全 task PASS）
- `./gradlew :app:lintDebug` 成功
- 追加した単体テスト件数:
  - `StarredItemsRepositoryImplTest`: 10 件
  - `StarredCardModelMapperTest`: 6 件
  - `StarredViewModelTest`: 8 件
- 既存テストは破壊していない（全 task PASS）

STATUS: complete
