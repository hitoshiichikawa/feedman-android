# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-36-impl-article-detail-sheet
- HEAD commit: 05b440f
- Compared to: origin/main..HEAD
- 変更ファイル（11 件）:
  - `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailContentPolicy.kt`（新規）
  - `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailUiState.kt`（新規）
  - `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailViewModel.kt`（新規）
  - `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailSheet.kt`（新規）
  - `app/src/main/kotlin/com/feedman/android/shell/AppShell.kt`（最小結線）
  - `app/src/main/kotlin/com/feedman/android/shell/Navigation.kt`（最小結線）
  - `app/src/main/res/values/strings.xml`（文言追加）
  - `app/src/test/kotlin/.../ArticleDetailContentPolicyTest.kt`（新規）
  - `app/src/test/kotlin/.../ArticleDetailViewModelTest.kt`（新規）
  - `docs/specs/36-article-detail-bottom-sheet-ui/{requirements.md,impl-notes.md}`
- 実行確認: `./gradlew :app:testDebugUnitTest --tests "com.feedman.android.feature.articledetail.*"` — BUILD SUCCESSFUL（既存テスト含めて緑）

## Verified Requirements

- 1.1 — `ArticleDetailViewModel.open` → Loading→Content 遷移 / `ArticleDetailSheet` が Hidden 以外で `FeedmanSheet` 描画 / `ArticleDetailViewModelTest`「open は Loading_を経由して…」
- 1.2 — `SourceRow` の `Favicon` + フィード識別表示 + `RelativeTimeFormatter` で公開日時表示
- 1.3 — `SourceRow` が `faviconValue = null` を渡し、`Favicon` の既存ロジックでレターアバターに fallback
- 1.4 — `FeedmanSheet`（ModalBottomSheet）の標準スクリム/ドラッグ動作 + `SourceRow` の閉じる IconButton + `ArticleDetailViewModelTest`「dismiss で Hidden に戻る」
- 1.5 — `ArticleDetailSheetContent` 内 `BackHandler(enabled = true) { onDismiss() }`
- 2.1 — `ContentPreview` の `heightIn(max = 200.dp)` + `Brush.verticalGradient(Transparent → surface)`
- 2.2 — `if (showExpandToggle) TextButton(label = "続きを読む")`（展開状態 false のとき）
- 2.3 — `TextButton onClick = { expanded = !expanded }` で `heightIn` 制約解除
- 2.4 — 同ボタンの label が `expanded` のとき「折りたたむ」に切替
- 2.5 — `AndroidView(TextView)` + `Html.fromHtml(FROM_HTML_MODE_COMPACT)` で HTML 要素表示 / `ArticleDetailContentPolicyTest`「resolvePreview は content が非空のとき…」
- 2.6 — `ContentPreview` の `htmlOrText == null` 分岐でプレースホルダ / `showExpandToggle` false で展開ボタン非表示 / `ArticleDetailContentPolicyTest` null/blank ケース
- 3.1 — `fetchAndApply` で `Content(isRead = true)` を即時 emit / Test「open は…isRead を true にする」
- 3.2 — `repository.updateState(isRead = true)` 発火 / Test の `updateStateCalls` 検証
- 3.3 — `rollbackReadIfStillSameContent` + `_events.emit(MarkReadFailed)` / `LaunchedEffect` で snackbar 表示 / Test「既読化サーバー反映失敗で…」
- 3.4 — `Content.isRead=true` を単一 state として参照（楽観反映中の描画整合）
- 3.5 — `if (!detail.isRead) { ... }` ガード / Test「既に既読の記事を open しても updateState を再送しない」
- 4.1 — `FooterActionBar` を本文スクロール領域（`weight(1f, fill = false).verticalScroll`）の外側 Column に固定配置
- 4.2 — `Button onClick = onOpenExternal` → `onOpenExternalRequested(detail.link)` 発火（実体は #37 領分の no-op stub）
- 4.3 — `markReadOnOpenExternal` の `if (current.isRead) return` ガード / Test 2 ケース（既読時 no-op / 未読時発火）
- 4.4 — `toggleStar` で `_uiState.value = current.copy(isStarred = next)` 即時反映 / Test「toggleStar で isStarred を即時トグル」
- 4.5 — `rollbackStarIfStillSameContent` + `_events.emit(StarUpdateFailed)` / Test「スター更新失敗時にロールバック」
- 4.6 — `MetaRow` と `FooterActionBar` が同一 `state.isStarred` を参照
- 5.1 — `MetaRow` の `Row(HatebuBadge + Spacer + StarToggle)`
- 5.2 — 既存 `HatebuBadge` / `HatebuLogic` の挙動を再利用（`hatebu_fetched_at` null 時の表示は既存ロジック担保）
- 5.3 — Req 4.6 と同じく単一 `Content.isStarred` を二箇所で参照
- 6.1 — `SheetLoadingBody` の `CircularProgressIndicator`
- 6.2 — `SheetErrorBody` で `ErrorFullScreen` 再利用 / Test「取得失敗で Error 状態へ遷移」
- 6.3 — `retry()` が Error 状態の itemId で再 open / Test「retry は Error 状態から再取得」
- 6.4 — `FooterActionBar` は `Content` 状態にのみ描画されるため、`detail.link` 取得完了後にのみフッタ主ボタンが現れる構造
- NFR 1.1 — `ModalBottomSheet` 既定アニメーション（標準フレーム）
- NFR 1.2 — 各タップ標的に `sizeIn(minHeight = 44.dp)` / `feedmanDimens.minTapTarget` / 主ボタン 46dp
- NFR 1.3 — フッタは本文 `verticalScroll` の外側 Column に固定（アニメ中も独立操作可能）
- NFR 2.1 — `MaterialTheme.colorScheme` / `feedmanColors` を参照（直値リテラル無し）
- NFR 2.2 — `Brush.verticalGradient(Transparent → MaterialTheme.colorScheme.surface)` でテーマ追従
- NFR 3.1 — `FeedmanSheet(label = stringResource(R.string.article_detail_sheet_pane_title))`
- NFR 3.2 — 閉じる / 元記事を開く / スター / 続きを読む の各ボタンに contentDescription または可視ラベル

## Boundary 確認

- 変更ファイルは `feature/articledetail/`（新規）、`shell/AppShell.kt`・`shell/Navigation.kt`（タイムラインから `open(id)` を結線する最小差分のみ）、`strings.xml`（文言追加のみ）、`app/src/test/`（純粋ロジック / ViewModel 単体テスト）。指示された boundary 内に収まっている。
- #37 領分（Custom Tabs 起動の実体）に踏み込まず、`onOpenExternal` を no-op stub として受けるに留めている（`AppShell.kt` 内の `/* TODO(#37): Custom Tabs 起動 */`）。
- #38 領分（画面横断状態同期）に踏み込まず、シート内整合（`Content` 単一 state を MetaRow と FooterActionBar の両方が参照）のみで完結している。
- 一覧側（timeline 等）は `Navigation.kt` で `onOpenItemDetail = { itemId -> onOpenItemDetail(itemId) }` を結ぶだけで、`TimelineScreen` 自身や ViewModel への変更は無い。
- `BASE_BRANCH=main` を前提に `git diff` を実施し、想定外のファイル変更は無いことを確認。
- Feature Flag Protocol は CLAUDE.md で `**採否**: opt-out` のため、flag 観点（旧パス温存 / `if (flag)` 分岐等）は適用対象外。

## Findings

なし。

## Summary

要件 1〜6 と NFR 1〜3 のすべての numeric AC について、ViewModel 単体テスト（10 ケース）と純粋ロジックテスト（10 ケース）でカバレッジが確保され、Composable 描画は instrumented 領分のため JVM テスト不在は missing test に該当しない。boundary は `feature/articledetail/` + シェル最小結線 + `strings.xml` + テストに閉じ、#37 / #38 領分には踏み込んでいない。`./gradlew :app:testDebugUnitTest` の articledetail スイートは緑。

RESULT: approve
