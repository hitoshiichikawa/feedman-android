# Implementation Notes — Issue #28

## 概要

Issue #28「Reusable loading/empty/error toast and sheet primitives」を実装した。
`core/ui` 配下に状態表示プリミティブ（StateViews）・通知ヘルパ（FeedmanSnackbar）・
ボトムシート共通枠（FeedmanSheet）と、フッター状態判定の純粋関数（ListFooterState）を
追加し、JVM 単体テストで状態判定ロジックと境界検証を担保した。各画面への組み込みは
Issue #52 ほか後続 Issue の領分（Req 7.2）であり、本 PR では `core/ui` 追加に閉じる。

## 追加ファイル

| ファイル | 役割 |
|---|---|
| `app/src/main/kotlin/com/feedman/android/core/ui/ListFooterState.kt` | フッター 4 状態の sealed interface と排他判定の純粋関数 |
| `app/src/main/kotlin/com/feedman/android/core/ui/StateViews.kt` | LoadingFullScreen / LoadingFooter / EmptyState / ErrorFullScreen / ErrorFooter / EndOfListFooter |
| `app/src/main/kotlin/com/feedman/android/core/ui/FeedmanSnackbar.kt` | SnackbarHostState ベースの通知ヘルパ（show / showWithAction / showAndReplaceCurrent） |
| `app/src/main/kotlin/com/feedman/android/core/ui/FeedmanSheet.kt` | ModalBottomSheet をラップした共通シート枠 |
| `app/src/test/kotlin/com/feedman/android/core/ui/ListFooterStateTest.kt` | フッター状態判定テスト（5 ケース） |
| `app/src/test/kotlin/com/feedman/android/core/ui/FeedmanSnackbarTest.kt` | メッセージ / アクションラベル境界テスト（5 ケース） |
| `app/src/test/kotlin/com/feedman/android/core/ui/FeedmanSheetLabelTest.kt` | シートラベル境界テスト（3 ケース） |
| `app/src/main/res/values/strings.xml` | Issue #28 用文字列リソース（5 件追加） |

## Requirement → テスト対応表

| Requirement ID | カバー方法 | テスト |
|---|---|---|
| Req 1.1 初回フルスクリーンローディング | `LoadingFullScreen` Composable | （NFR 3.2: instrumented 領分） |
| Req 1.2 追加フッターローディング | `LoadingFooter` Composable / `ListFooterState.Loading` の優先順位判定 | `ListFooterStateTest.Req 1_2 append loading true returns Loading` / `Req 4_2 loading prioritized over error and end of pagination` |
| Req 1.3 初回 / 追加の別コンポーネント化 | `LoadingFullScreen` と `LoadingFooter` を別関数として公開 | （API 上の宣言で担保） |
| Req 2.1 空状態の縦中央配置 | `EmptyState` Composable（`Arrangement.Center` + `Alignment.CenterHorizontally`） | （NFR 3.2: instrumented 領分） |
| Req 2.2 アイコン / 主題 / 補助の差し替え可能 | `EmptyState(icon, title, subtitle, ...)` の引数化 | （API 上の宣言で担保） |
| Req 2.3 補助テキスト省略可 | `subtitle: String? = null` で `null` のとき非表示 | （API 上の宣言で担保） |
| Req 2.4 FMEmpty 同等構図 | 56dp 角丸 16dp アイコン背景 / 主題 15sp SemiBold / 補助 13sp mutedFg 最大幅 240dp / gap 12dp / padding 40dp | （prototype と照合済み） |
| Req 3.1 初回フルスクリーンエラー | `ErrorFullScreen` Composable | （NFR 3.2: instrumented 領分） |
| Req 3.2 再試行ハンドラ呼び出し | `onRetry: () -> Unit` を `TextButton(onClick = onRetry)` に直結 | （API 上の宣言で担保） |
| Req 3.3 エラーメッセージ差し替え可 | `message: String? = null`（null のとき既定文字列リソース） | （API 上の宣言で担保） |
| Req 3.4 追加エラーフッター | `ErrorFooter` Composable / `ListFooterState.Error` の優先順位判定 | `ListFooterStateTest.Req 3_4 append error true returns Error` / `Req 4_2 error prioritized over end of pagination` |
| Req 4.1 終端フッター | `EndOfListFooter` Composable / `ListFooterState.EndOfList` 判定 | `ListFooterStateTest.Req 4_1 end of pagination true returns EndOfList` |
| Req 4.2 排他描画 | `resolveListFooterState` の優先順位（Loading > Error > EndOfList > None） | `ListFooterStateTest` 全ケース（5 件）で排他性を検証 |
| Req 4.3 終端文言の統一 | `R.string.state_end_of_list = "最後まで読みました"` を単一参照 | （リソース ID 1 つに集約 / コードレビュー時点） |
| Req 5.1 スケジュール | `FeedmanSnackbar.show` が `SnackbarHostState.showSnackbar` に委譲 | （API 上の宣言で担保） |
| Req 5.2 同時 1 件保証 | `SnackbarHostState` の順次キュー（既定）と `showAndReplaceCurrent`（即時置換）の 2 方式 | （Material 3 仕様で保証） |
| Req 5.3 一定時間で自動消去 | `SnackbarDuration.Short` / アクションありは `Long` | （Material 3 仕様で保証） |
| Req 5.4 内部に固定文言を持たない | `validateMessage(message)` が空 / 空白のみを拒否し fallback を提供しない | `FeedmanSnackbarTest.Req 5_4 empty message is rejected` / `Req 5_4 whitespace only message is rejected` / `non empty message passes validation` |
| Req 5.5 アクション付き通知 | `showWithAction(hostState, message, actionLabel, onAction)` で `ActionPerformed` 時のみ `onAction` 呼出 | `FeedmanSnackbarTest.Req 5_5 empty action label is rejected` / `Req 5_5 valid action label passes` |
| Req 6.1 ドラッグハンドル / 上端角丸 / 下端セーフエリア | `FeedmanSheet` の `dragHandle` slot で 40x5 角丸 999 を描画、角丸は `RoundedCornerShape(topStart=16dp, topEnd=16dp)`、`contentWindowInsets = { WindowInsets.navigationBars }` | （NFR 3.2: instrumented 領分） |
| Req 6.2 任意内容差し込み | `content: @Composable () -> Unit` slot 引数 | （API 上の宣言で担保） |
| Req 6.3 閉鎖ハンドラ | `onDismissRequest: () -> Unit` を `ModalBottomSheet.onDismissRequest` に直結 | （Material 3 仕様で標準挙動） |
| Req 6.4 アクセシビリティラベル | `Modifier.semantics { paneTitle = label }` / `FeedmanSheetLabel.validate` で空白拒否 | `FeedmanSheetLabelTest.Req 6_4 empty label is rejected` / `Req 6_4 whitespace only label is rejected` / `Req 6_4 valid label passes` |
| Req 6.5 視覚仕様 | プロト `FMSheet` 基準 + SPEC §8 デザイントークン整合（角丸 16dp、scrim は `feedmanColors.scrim`） | （prototype + design token と照合済み） |
| Req 7.1 core/ui 配置 | 全ファイルが `app/src/main/kotlin/com/feedman/android/core/ui/` 配下 | （ファイルレイアウトで担保） |
| Req 7.2 画面組み込みなし | 既存画面・シートのコード未変更（追加のみ） | （git diff で担保） |
| Req 7.3 公開 API として再利用可能 | 全 Composable / object に KDoc + 引数説明を付与 | （ソースコード上） |
| NFR 1.1 デザイントークン参照 | `feedmanColors` / `feedmanDimens` / `MaterialTheme.colorScheme` から取得 | （ハードコード値なし） |
| NFR 1.2 最小 44dp タップ標的 | `ErrorFullScreen` / `ErrorFooter` の TextButton が `sizeIn(minHeight = 44.dp)` | （API 上の宣言で担保） |
| NFR 1.3 ライト / ダーク両対応 | `feedmanColors` がテーマ追従、固定色を使わない | （tokens 経由で担保） |
| NFR 2.1 文字列リソース化 | `state_loading_description` / `state_error_default_message` / `state_error_retry` / `state_end_of_list` / `sheet_drag_handle_description` を `strings.xml` に追加 | （リソースで担保） |
| NFR 3.1 JVM テスト可能な状態判定単位 | `resolveListFooterState` / `FeedmanSnackbar.validateMessage` / `FeedmanSheetLabel.validate` | テスト 3 ファイル合計 13 ケース |
| NFR 3.2 Compose 描画は instrumented 領分 | 本 PR の JVM テスト対象外と明記 | （本表で明示） |

## 判断記録

### 1. 角丸サイズ（プロト 22dp vs SPEC §8 の 10-16dp）

`design/mobile/fm-sheets.jsx` の `FMSheet` は `borderTopLeftRadius: 22` を採用しているが、
本リポジトリの `FeedmanDimens.cornerLarge = 16.dp`（SPEC §8 の角丸上限値）を採用した
（NFR 1.1）。SPEC §8 の「角丸 10-16px」が正本でありプロト値より優先される。

### 2. 終端文言「最後まで読みました」

SPEC §6 の「最後まで読みました」をそのまま採用し、`strings.xml` の
`state_end_of_list` リソース 1 件に集約した。アプリ全体で他の文言が混在しないよう
コードレビュー時にリソース ID 経由のみを許可する運用とする（Req 4.3）。

### 3. Snackbar の置換 vs 順次キュー

Req 5.2 は「後続が前を置き換えるか順次表示される」と並列に要求しているため、
両方をサポートする 2 つの API を提供した:

- `show` / `showWithAction`: 既定の順次キュー（`SnackbarHostState` の標準挙動）
- `showAndReplaceCurrent`: 現在表示中を `dismiss()` して新規表示

呼び出し側が用途に応じて選択する。

### 4. 空メッセージ拒否

Req 5.4「ヘルパ内部に固定文言を持たない」を機械的に強制するため、`validateMessage` で
空文字列 / 空白のみのメッセージを `IllegalArgumentException` で拒否する設計とした。
fallback を一切持たないことを境界テストで担保。

### 5. FeedmanSheet のラベル空白拒否

Req 6.4「スクリーンリーダーから参照可能にする」を満たすため、空白ラベルは
`FeedmanSheetLabel.validate` で例外を投げる。スクリーンリーダーが空文字列を読み上げ
できない仕様を考慮した境界処理。

### 6. テスト粒度

NFR 3.2「Compose UI コンポーネント自体の描画検証 shall instrumented テストの領分とし、
本 Issue では JVM テストの必須対象としない」に従い、JVM テストは状態判定と境界検証に
限定した。Composable 自体の描画（角丸・余白・色）は instrumented 領分とし、本 Issue
範囲外（Out of Scope）。

### 7. 既定アイコンの提供

`DefaultEmptyStateIcon = Icons.Outlined.Inbox` を公開した。プロトでは
`FMEmpty icon="inbox" / "star" / "search"` の 3 種を使い分けているが、`Inbox` を
共通の既定として提供し、各画面 Issue が場面に応じて差し替える（Req 2.2）。

## 確認事項（PR 本文向け）

- **角丸 22dp → 16dp 採用**: プロト `FMSheet` の 22dp と SPEC §8 のトークン上限 16dp に
  乖離があるため、SPEC §8 を優先した（NFR 1.1）。プロト準拠を望む場合は
  `FeedmanDimens.cornerLarge` の値変更で全シート一括で追従できる
- **Snackbar の 2 方式 API**: 順次キュー（既定 `show`）と即時置換（`showAndReplaceCurrent`）の
  どちらをデフォルトとするかは画面組み込み Issue で議論される想定
- **Inbox 既定アイコン**: 検索画面 / スター画面では `search` / `star` を渡すことを
  想定。Issue #52 ほかの画面実装で適切なアイコンを選択する
- **既定エラー文言**: `state_error_default_message = "読み込みに失敗しました"` を
  既定にしているが、画面側で API エラーコード由来の具体的メッセージに差し替えるのを
  推奨する（Req 3.3 は差し替え可を要求）

## 派生タスク候補

- Issue #52（横断タイムライン）以降の各画面で、本プリミティブを使った組み込み
- Compose UI instrumented テストでの描画検証（NFR 3.2 の領分 / 別 Issue 提案）
- `FeedmanSheet` を使った既存 `FMDetailSheet` / `FMSettingsSheet` 相当の各シート実装

## ビルド結果

`./gradlew build` 成功（ローカル / `BUILD SUCCESSFUL in 2m 8s`）。
ユニットテスト: 既存テストを含め全パス。新規追加 13 ケース全パス
（ListFooterState 5 / FeedmanSnackbar 5 / FeedmanSheetLabel 3）。

## opt-in 機能の利用

CLAUDE.md `## Feature Flag Protocol` `**採否**: opt-out` を確認した。Feature Flag Protocol
は未採用のため、本 PR は flag 裏実装パターンを使わず通常の単一実装パスで実装した。

STATUS: complete
