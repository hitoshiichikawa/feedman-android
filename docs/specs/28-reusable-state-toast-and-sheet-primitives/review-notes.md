# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-28-impl-state-toast-sheet-primitives
- HEAD commit: b5775bf
- Compared to: origin/main..HEAD
- Feature Flag Protocol: opt-out（追加観点なし）

## Verified Requirements

- 1.1 — `StateViews.kt` の `LoadingFullScreen` Composable がコンテンツ領域全体（`fillMaxSize` + `Alignment.Center`）に `CircularProgressIndicator` を描画
- 1.2 — `StateViews.kt` の `LoadingFooter` Composable（24dp インジケータ単独、padding 16dp）／`ListFooterState.Loading` の優先順位判定（`ListFooterStateTest.Req 1_2 append loading true returns Loading`）
- 1.3 — `LoadingFullScreen` と `LoadingFooter` を別関数として公開（API 上の宣言で担保）
- 2.1 — `EmptyState` が `Arrangement.Center` + `Alignment.CenterHorizontally` でアイコン → 主題 → 補助の縦並びを構成
- 2.2 — `EmptyState(icon, title, subtitle, iconContentDescription)` 引数化により呼び出し側が差し替え可能。`DefaultEmptyStateIcon` も公開
- 2.3 — `subtitle: String? = null` で null のとき非表示分岐あり（`if (subtitle != null)`）
- 2.4 — 56dp 角丸 16dp の muted 背景、主題 15sp SemiBold、補助 13sp mutedFg 最大幅 240dp、padding 40dp（プロト `FMEmpty` 同等構図を踏襲、`impl-notes.md` 表に照合済み）
- 3.1 — `ErrorFullScreen` がコンテンツ領域全体（`fillMaxSize` + padding 40dp）にメッセージと再試行ボタンを描画
- 3.2 — `TextButton(onClick = onRetry)` で 1 回呼び出し直結
- 3.3 — `message: String? = null` 引数で null のとき `R.string.state_error_default_message` を fallback、差し替え可
- 3.4 — `ErrorFooter` Composable / `ListFooterState.Error`（`ListFooterStateTest.Req 3_4 append error true returns Error` / `Req 4_2 error prioritized over end of pagination`）
- 4.1 — `EndOfListFooter` Composable / `ListFooterState.EndOfList`（`ListFooterStateTest.Req 4_1 end of pagination true returns EndOfList`）
- 4.2 — `resolveListFooterState` の優先順位（Loading > Error > EndOfList > None）— `ListFooterStateTest` 全 6 ケースで排他性検証
- 4.3 — `R.string.state_end_of_list = "最後まで読みました"` を `strings.xml` に集約。`EndOfListFooter` から単一参照
- 5.1 — `FeedmanSnackbar.show` が `SnackbarHostState.showSnackbar` に委譲してスケジュール
- 5.2 — `SnackbarHostState` 既定の順次キュー + `showAndReplaceCurrent`（`currentSnackbarData?.dismiss()` で即時置換）の 2 方式 API
- 5.3 — `SnackbarDuration.Short` / アクションありは `SnackbarDuration.Long` で自動消去
- 5.4 — `validateMessage` が空白を `IllegalArgumentException` で拒否（`FeedmanSnackbarTest.Req 5_4 empty message is rejected` / `Req 5_4 whitespace only message is rejected`）。ヘルパ内に固定文言なし
- 5.5 — `showWithAction(hostState, message, actionLabel, onAction)` が `SnackbarResult.ActionPerformed` 時のみ `onAction` を呼ぶ。`validateActionLabel` で空白拒否（`FeedmanSnackbarTest.Req 5_5 empty action label is rejected` / `Req 5_5 valid action label passes`）
- 6.1 — `ModalBottomSheet` ラッパで上端中央 40x5 角丸 999 ドラッグハンドル、`RoundedCornerShape(topStart=16dp, topEnd=16dp)` 角丸、`contentWindowInsets = { WindowInsets.navigationBars }` でセーフエリア確保
- 6.2 — `content: @Composable () -> Unit` slot 引数
- 6.3 — `onDismissRequest` を `ModalBottomSheet.onDismissRequest` に直結（Material 3 標準挙動）
- 6.4 — `Modifier.semantics { paneTitle = label }` でスクリーンリーダー参照可能。`FeedmanSheetLabel.validate` で空白拒否（`FeedmanSheetLabelTest.Req 6_4 empty label is rejected` / `Req 6_4 whitespace only label is rejected` / `Req 6_4 valid label passes`）
- 6.5 — プロト `FMSheet` 基準、角丸は SPEC §8 トークン上限 16dp に整合（`feedmanColors.scrim`、`feedmanDimens` 経由）。`impl-notes.md` で 22dp → 16dp 採用の判断記録あり
- 7.1 — 全ファイルが `app/src/main/kotlin/com/feedman/android/core/ui/` 配下に配置
- 7.2 — `git diff --name-only origin/main..HEAD` の結果、`core/ui/`・`strings.xml`・`app/src/test/core/ui/`・`docs/specs/28-.../` のみで既存画面 / シートの変更なし
- 7.3 — 全 Composable / object に KDoc + 引数説明を付与（StateViews.kt / FeedmanSnackbar.kt / FeedmanSheet.kt の冒頭ブロックコメントで確認）
- NFR 1.1 — `MaterialTheme.feedmanColors` / `MaterialTheme.feedmanDimens` / `MaterialTheme.colorScheme` 経由でデザイントークン参照（`FeedmanTheme.kt` の既存定義と整合）
- NFR 1.2 — `ErrorFullScreen` の TextButton が `sizeIn(minWidth=88dp, minHeight=44dp)`、`ErrorFooter` の TextButton が `sizeIn(minWidth=64dp, minHeight=44dp)` で 44dp タップ標的確保
- NFR 1.3 — `feedmanColors` がテーマ追従、固定色を使わない（コード内に hex 値なし、`mutedFg` / `borderStrong` / `scrim` 経由）
- NFR 2.1 — `state_loading_description` / `state_error_default_message` / `state_error_retry` / `state_end_of_list` / `sheet_drag_handle_description` を `strings.xml` に追加し、コード上でハードコードなし
- NFR 3.1 — `resolveListFooterState` / `FeedmanSnackbar.validateMessage` / `FeedmanSnackbar.validateActionLabel` / `FeedmanSheetLabel.validate` を JVM 単体テスト対象として分離し、合計 13 ケース（6 + 5 + 2 + 3）でカバー（`./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.ui.*"` で BUILD SUCCESSFUL を確認）
- NFR 3.2 — `impl-notes.md` および本 PR で Compose UI 描画検証を JVM テスト対象外（instrumented 領分）として明記

## Findings

なし

## Summary

requirements.md の Req 1〜7 すべての numeric ID と NFR 1〜3 が `core/ui/` 配下の 4 ファイル
（ListFooterState / StateViews / FeedmanSnackbar / FeedmanSheet）と `strings.xml` の 5 件追加で
カバーされており、状態判定 / 境界検証ロジックが JVM テスト 13 ケースで担保される。
変更範囲は `core/ui/`・`strings.xml`・`app/src/test/core/ui/`・`docs/specs/28-.../` のみで
既存画面 / シートへの組み込みは行われておらず boundary 内（Req 7.2 と整合）。
`./gradlew :app:testDebugUnitTest --tests "com.feedman.android.core.ui.*"` BUILD SUCCESSFUL。

RESULT: approve
