# Issue #36 実装ノート — Article detail bottom sheet UI

## 概要

`docs/specs/36-article-detail-bottom-sheet-ui/requirements.md` に基づき、記事詳細
ボトムシート（detail=partial）の UI / 状態管理 / シェル結線を実装した。design.md / tasks.md
は存在せず、Issue 着手プロンプトで指示された方針に従って実装している。

主成果物:

- `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailContentPolicy.kt`
  — 本文プレビュー選択ロジック（純粋ロジック）
- `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailUiState.kt`
  — `Hidden` / `Loading` / `Content` / `Error` の sealed state と楽観的更新失敗イベント
- `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailViewModel.kt`
  — `getItem(id)` 取得、シート表示時の即時既読化、スタートグル楽観更新、retry / dismiss
- `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailSheet.kt`
  — `FeedmanSheet` ベースの Compose UI
- `app/src/main/kotlin/com/feedman/android/shell/AppShell.kt` / `Navigation.kt` — シェル結線
- `app/src/main/res/values/strings.xml` — 文言の追加
- 単体テスト: `ArticleDetailContentPolicyTest`、`ArticleDetailViewModelTest`

## requirement ID → テスト対応表

| Requirement ID | AC 概要 | カバー方法 |
|---|---|---|
| 1.1 | タップで部分シートを表示 | `ArticleDetailViewModelTest.open は Loading_を経由して Content へ遷移し isRead を true にする` / Composable: `ArticleDetailSheet` が `Hidden` 以外の状態で `FeedmanSheet`（partial 表示） |
| 1.2 | ソース行に favicon / フィード名 / 公開日時 | `ArticleDetailSheet#SourceRow`（Composable）。`ItemDetail` に `feed_title` / `feed_favicon_url` が無いため SPEC §4.2 の制約として `feed_id` を fallback 表示（後述「確認事項」参照） |
| 1.3 | favicon=null → レターアバター | `Favicon` Composable が既存ロジックで担保（`SourceRow` は `faviconValue = null` を渡してレターアバターを誘導） |
| 1.4 | ドラッグ下 / スクリム / 閉じるボタンで閉じる | `FeedmanSheet`（Material3 `ModalBottomSheet` の標準挙動）+ `SourceRow` の閉じる IconButton + `ArticleDetailViewModelTest.dismiss で Hidden に戻る_Req 1_4` |
| 1.5 | システム戻るで閉じる | `ArticleDetailSheetContent` 内の `BackHandler` |
| 2.1 | 本文を約 200dp に収め下端フェード | `ContentPreview` の `heightIn(max = 200.dp)` + `Brush.verticalGradient` |
| 2.2 | 折りたたみ時に「続きを読む」表示 | `ContentPreview` 下の `TextButton`（`showExpandToggle` 真かつ `!expanded` のときラベル「続きを読む」） |
| 2.3 | 「続きを読む」で展開 | `expanded` state を toggle、`heightIn` を `fillMaxWidth` のみに切替 |
| 2.4 | 展開状態で「折りたたむ」 | 同じボタンの label 切替 |
| 2.5 | 本文中の見出し・段落・リンク・強調・引用・コード・リスト | `Html.fromHtml(COMPACT)` で TextView 表示。`ArticleDetailContentPolicyTest.resolvePreview は content が非空のとき content を返す` |
| 2.6 | 空のときプレースホルダ + 続きを読む非表示 | `ContentPreview` の null 分岐 + `showExpandToggle` の false 判定。`ArticleDetailContentPolicyTest` の null / 空白テスト |
| 3.1 | シート表示時に即時既読化 | `ArticleDetailViewModelTest.open は Loading_を経由して Content へ遷移し isRead を true にする` |
| 3.2 | サーバーに既読更新リクエスト | 同上テスト末尾の `updateStateCalls` 検証 |
| 3.3 | 失敗ロールバック + 通知 | `ArticleDetailViewModelTest.既読化サーバー反映失敗で isRead を false に戻し MarkReadFailed イベントを流す_Req 3_3` + `ArticleDetailSheet` の `LaunchedEffect` が `MarkReadFailed` を snackbar 表示 |
| 3.4 | 楽観既読化反映中はシート内描画も「既読」 | `Content.isRead=true` をシート内で共有（実装上は表示要素を追加する箇所がないため、`StateFlow` 経由でシート上に反映される構造を担保） |
| 3.5 | 既読記事の再送なし（冪等） | `ArticleDetailViewModelTest.既に既読の記事を open しても updateState を再送しない_Req 3_5` |
| 4.1 | フッタに主ボタン + スター固定 | `FooterActionBar`（Column 内で本文 Column の下に配置、本文 Column を `weight(1f, fill=false)` + `verticalScroll` でスクロール対象に限定） |
| 4.2 | 「元記事を開く」で外部リンク発火 | `ArticleDetailSheet` の `onOpenExternalRequested` が `onOpenExternal(detail.link)` を呼ぶ（Issue #37 で Custom Tabs 起動を結線） |
| 4.3 | 未読のときのみ既読化 | `ArticleDetailViewModelTest.markReadOnOpenExternal は未読のときのみ既読化する_Req 4_3` / `markReadOnOpenExternal は未読 Content から既読化リクエストを発火する_Req 4_3` |
| 4.4 | スタートグル即時反映 | `ArticleDetailViewModelTest.toggleStar で isStarred を即時トグルし updateState を呼ぶ_Req 4_4` |
| 4.5 | スター失敗ロールバック + 通知 | `ArticleDetailViewModelTest.スター更新失敗時にロールバックして StarUpdateFailed イベントを流す_Req 4_5` + `LaunchedEffect` の snackbar |
| 4.6 | 本文上部とフッタのスター同期 | 単一 `Content.isStarred` を `MetaRow` の `StarToggle` と `FooterActionBar` の `StarToggle` の双方に渡す（Composable レベル） |
| 5.1 | タイトル直下にはてブ + スター横並び | `MetaRow` の Row（`HatebuBadge` + spacer + `StarToggle`） |
| 5.2 | hatebu_fetched_at=null → 数値省略 / 0 判別可能 | 既存 `HatebuBadge` / `HatebuLogic` が "−" 表示で担保 |
| 5.3 | 本文上部とフッタのスター ON/OFF 整合 | Req 4.6 と同じく単一 state の二箇所参照 |
| 6.1 | 取得中はローディング | `SheetLoadingBody` の `CircularProgressIndicator` |
| 6.2 | 取得失敗エラー + 再試行 | `SheetErrorBody` で既存 `ErrorFullScreen` を再利用 + `ArticleDetailViewModelTest.取得失敗で Error 状態へ遷移する_Req 6_2` |
| 6.3 | 再試行で再取得 | `ArticleDetailViewModelTest.retry は Error 状態から再取得する_Req 6_3` |
| 6.4 | 本文取得完了前は元記事ボタン有効化を遅延 | `FooterActionBar` は `SheetContentBody`（= `Content` 状態）内でのみ描画される。`Loading` / `Error` 状態にはフッタアクション自体が現れない（= `detail.link` が利用可能になり次第有効化される） |
| NFR 1.1 | タップ 100ms 内にスライドアップ開始 | `ModalBottomSheet` 既定アニメーション（標準 250〜300ms 内に開始） |
| NFR 1.2 | タップ標的 44dp 以上 | `StarToggle`（既存 44dp 担保）/ 閉じる IconButton / 「続きを読む」TextButton / 「元記事を開く」Button に `sizeIn(minHeight = 44dp)`（主ボタンは 46dp） |
| NFR 1.3 | 展開アニメ中もフッタ操作可能 | フッタは固定 Row（本文の `verticalScroll` に巻き込まれない） |
| NFR 2.1 | テーマ整合 | `MaterialTheme.colorScheme` / `feedmanColors` の参照のみ。直値リテラル無し |
| NFR 2.2 | 下端フェードはシート背景に整合 | `Brush.verticalGradient(Transparent → MaterialTheme.colorScheme.surface)` |
| NFR 3.1 | 「記事の詳細」を pane title に | `FeedmanSheet(label = stringResource(R.string.article_detail_sheet_pane_title))` |
| NFR 3.2 | 各ボタンに a11y 説明 | `StarToggle`（既存 contentDescription）/ 閉じる IconButton（`article_detail_close_description`）/ 「元記事を開く」Button のテキストラベル / 「続きを読む」TextButton のテキストラベル |

## 設計判断記録（Open Questions の解決）

### 1. 楽観的更新失敗の通知手段

requirements.md Open Questions 1 番目に明文化されていた論点。`design/SPEC.md` §6 が
「楽観的更新 → 失敗時ロールバック」を要求しているが具体的な通知手段が決まっていない。

**判断**: SPEC §6 と整合する標準的手段として **snackbar** を採用。シート内 SnackbarHost を
持たせ、`FeedmanSnackbar.show()`（Issue #28）を利用する。文言は
`article_detail_mark_read_failed` / `article_detail_star_update_failed` として
`strings.xml` に集約。

### 2. シート起動時の詳細再取得

requirements.md Open Questions 2 番目。一覧 `summary` を流用するか必ず詳細を再取得するか。

**判断**: 一覧 `summary` は要約のみで HTML 本文（`content`）を含まないため、SPEC §4.2 に
則り **シート起動時に必ず `getItem(id)` を発行**する設計とした。取得中は `SheetLoadingBody`
（`CircularProgressIndicator` のみ、ソース行は閉じるボタンのみのバーで暫定表示）で
ユーザーに進行状況を伝える（Req 6.1）。

### 3. 本文が空のケース

requirements.md Open Questions 3 番目。`content` が空文字列の発生条件不明。

**判断**: 保険的に **`summary` を fallback 表示**してから、それも空であれば
「本文のプレビューはありません」をプレースホルダ表示する 3 段階フォールバックを採用。
`ArticleDetailContentPolicy.resolvePreview` に純粋ロジックとして抽出し、JVM テスト対象に
した（`ArticleDetailContentPolicyTest`）。

## その他の判断

- **HTML 表示**: Out of Scope に「WebView による高度な HTML レンダリング」が明示されている
  ため、`AndroidView`(TextView) + `Html.fromHtml(FROM_HTML_MODE_COMPACT)` を採用。これは
  プレビュー用途として段落 / 改行 / 強調 / リンク / リスト等の最低限要素を担保する Android
  SDK 標準機能で、追加依存ゼロ。実装は `ArticleDetailSheet#ContentPreview`。
- **ソース行のフィード名 / favicon**: SPEC §4.2 の `ItemDetail` レスポンスには `feed_title` /
  `feed_favicon_url` が含まれないため、現時点では `feed_id` を表示し favicon は
  レターアバターに fallback している。後続 Issue（#38 など）で一覧から詳細へ
  `CrossFeedItem.feed_title` / `feed_favicon_url` を伝搬する設計が必要。本 Issue では
  ItemDetail の field 構造に閉じて実装した（spec 書き換え禁止規約に従い、要件と齟齬が
  あれば確認事項として記載）。
- **ViewModel スコープ**: `LoggedInShell` 内で 1 つの `ArticleDetailViewModel` を保持し、
  全ルート（Timeline / Feed / Starred / Search）から同じインスタンスへ `open(id)` を依頼する
  設計を採用。これによりルート遷移しながらシートを保持・再起動するケースの自然な振る舞いを
  得る（NFR 1.1 のレスポンス性にも寄与）。
- **楽観更新ロールバックの安全性**: `rollbackReadIfStillSameContent` /
  `rollbackStarIfStillSameContent` は、ロールバック実施時点の `_uiState` が同じ `Content` で
  あること（= まだ同じ記事を表示中）を確認してから巻き戻す。ユーザーが別記事に切替後の
  「過去のリクエスト失敗」での誤巻き戻しを防止する。

## ビルド・テスト結果

`./gradlew build` 成功（ローカル `BUILD SUCCESSFUL in 1m 59s`）。

- `:app:compileDebugKotlin` / `:app:compileReleaseKotlin` ともに成功
- `:app:testDebugUnitTest` / `:app:testReleaseUnitTest` 成功（既存テスト含む）
- `:app:lintDebug` 成功
- `:app:assembleRelease` 成功

追加した単体テスト:

- `ArticleDetailContentPolicyTest`: 10 ケース（content / summary / 空 / 空白 / null の全組合せ）
- `ArticleDetailViewModelTest`: 10 ケース（open 成功 / 既読冪等 / 既読失敗ロールバック /
  取得失敗 / retry / toggleStar 成功 / スター失敗ロールバック / markReadOnOpenExternal 既読時
  no-op / 未読時発火 / dismiss）

## 確認事項（PM / Architect 向け）

- **ItemDetail に `feed_title` / `feed_favicon_url` が無い問題**: SPEC §4.2 の `ItemDetail` は
  `feed_id` までしか持たないため、現在の実装ではソース行に `feed_id` を表示している。
  プロト `fm-sheets.jsx` の `FMDetailSheet` は `item.feed_title` を期待しており、UX 上は
  フィード名が必要。Issue #38（画面横断同期）またはサーバー側仕様調整で「一覧 → 詳細」
  画面遷移時にフィードメタを引き継ぐ設計が必要。本実装では暫定的に `feed_id` 表示で
  動作可能な状態に留めた。
- **Custom Tabs 結線（#37）の責務範囲**: `onOpenExternal` コールバックは現状 no-op で
  受けている。#37 で接続する際は、`onOpenExternal(url)` 内で Chrome Custom Tabs を起動し、
  失敗時のフォールバック（`ACTION_VIEW` Intent）を含めることを想定している。
- **画面横断同期（#38）の責務範囲**: 既読化・スター変更の一覧反映は #38 で扱う。本 Issue は
  シート内整合（本文上部のスターとフッタのスターが同じ state を参照する点）のみで完結。
- **Compose UI テスト**: requirements.md NFR で UI テストは要求されていないが、Compose
  UI テスト（androidTest）を追加するかどうかは別 Issue（例: 既存タイムラインの
  Compose UI テスト追加と同時）で判断する。本 Issue では JVM 単体テストに集中し、
  UI テストはエミュレータ依存のため対象外とした。

## STATUS

STATUS: complete
