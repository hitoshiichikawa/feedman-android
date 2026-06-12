# Implementation Notes — Issue #45 登録後の購読再読込

## 概要

Issue #45 では、`#44` で導入されたフィード登録シートの成功イベントを契機として、
`#39` の `SubscriptionRepository.refresh()` を呼び出すワイヤリングを追加した。これにより
登録成功 → ドロワーの購読フィード一覧に新フィードが即時反映 → 直後にタップして記事一覧
へ遷移、という動線がシート操作だけで完結する（Req 1.1〜2.3）。

## 設計判断

### refresh() の呼び出し場所: `RegisterFeedViewModel.submit()` の成功パス

`RegisterFeedViewModel` のコンストラクタ引数に `SubscriptionRepository` を追加し、
`submit()` の成功直後（`repository.register()` 成功 → `RegistrationSucceeded` emit →
`close()` の **後**）に `triggerSubscriptionRefresh()` を呼ぶ構成とした。

選択肢として「AppShell の `onRegistrationSucceeded` コールバック内で呼ぶ」案もあったが、
以下の理由で ViewModel 側に置いた:

1. NFR 3.1 / 3.2 が「Subscription Repository をテストダブルで差し替えた **単体テスト**」を
   要求しており、JVM 単体テスト可能な ViewModel 層に閉じ込めるのが自然
2. ViewModel が登録応答の成功・失敗・キャンセル状態を一手に持っているため、Req 4.1 /
   4.2 / 4.3（失敗・キャンセル時に refresh を呼ばない）の網羅が catch 節分岐で機械的に
   担保できる
3. AppShell 側に refresh 呼び出しを置くと、`onRegistrationSucceeded` トーストと再取得の
   順序保証が UI 層に分散し、NFR 1.2（成功フィードバックが refresh 完了を待たない）の
   検証が UI 層テストになって難しい

### 再取得失敗の通知経路: 新規イベント `SubscriptionRefreshFailed`

`SubscriptionRepository.refresh()` の契約上、内部で例外を捕捉し `observeLoadState()` へ
Error 状態として通知する仕様（呼び出し元へ例外は投げない）。よって `try/catch` ではなく
`refresh()` 完了直後に `observeLoadState().first()` で結果状態を 1 回読み取り、
`SubscriptionLoadState.Error` であれば新規イベント `RegisterFeedEvent.SubscriptionRefreshFailed`
を emit する設計とした。

- AppShell は当該イベントを受領した場合、`shellSnackbarHostState` に非ブロッキングな
  snackbar を流す（新規 string resource `register_feed_subscription_refresh_failed`）。
- drawer 内のフィードセクションのエラー表示（Req 3.3 後段）は drawer 側が独自に
  `observeLoadState()` を購読しているため、本イベントとは独立に自動で反映される。
- 復帰経路（Req 3.4 / 3.5）は既存の SubscriptionRepository / DrawerViewModel の挙動でカバー
  されるため、本 Issue では追加のリトライロジックを書いていない（再取得は 1 回呼び切り）。

### NFR 1.2 の順序保証

`RegistrationSucceeded` を emit + `close()` 実行 → **その後** `triggerSubscriptionRefresh()` を
suspend 呼び出しする構成にすることで、refresh の完了を待たずに成功トースト・シート
クローズが先に進む。テスト
`submit 成功時に RegistrationSucceeded を refresh より先に emit する Req 1_3 NFR 1_2` で
`CompletableDeferred` 経由で refresh を suspend させても `RegistrationSucceeded` が先に
届くことを検証した。

## requirement ID → テスト対応表

| Req ID | 検証テスト |
|---|---|
| Req 1.1 | `submit 成功時に SubscriptionRepository_refresh が 1 回呼ばれる Req 1_1` |
| Req 1.2 | （観測経路）既存テスト `DrawerViewModelTest` / `SubscriptionRepositoryImplTest` の挙動に依存。`observeSubscriptions()` Flow 経由で drawer に流れる仕組みは本 Issue で変更していない |
| Req 1.3 | `submit 成功時に RegistrationSucceeded を refresh より先に emit する Req 1_3 NFR 1_2`、`submit 成功後の refresh 成功時には SubscriptionRefreshFailed は emit されない Req 1_3` |
| Req 1.4 | `submit 成功時に SubscriptionRepository_refresh が 1 回呼ばれる Req 1_1`（ユーザー追加操作なし、submit 直後に呼ばれる） |
| Req 2.1 / 2.2 / 2.3 | 既存挙動（#41 FeedScreen + #39 SubscriptionRepository.observeFeed）。本 Issue で `observeFeed` / Navigation の変更を行っていないため、`SubscriptionRepository.observeSubscriptions()` が新フィードを含むリストを流せば自然に成立する（テスト追加なし。impl-notes 末尾「確認事項」参照） |
| Req 3.1 | `submit 成功後の refresh 失敗時に SubscriptionRefreshFailed が emit され シートは閉じたまま Req 3_1 Req 3_2 Req 3_3`（RegistrationSucceeded が抑止されないことを確認） |
| Req 3.2 | 同上テスト（モーダル / 全画面エラーを表示しないことは `RegisterFeedUiState.Hidden` のまま維持で確認） |
| Req 3.3 | 同上テスト（`SubscriptionRefreshFailed` イベント発行）。 drawer 側の `observeLoadState` Error 表示は #39 で既に実装済み |
| Req 3.4 / 3.5 | 既存挙動（#39）に依存。本 Issue で SubscriptionRepository を変更していない |
| Req 4.1 | `submit 失敗 4xx 時は SubscriptionRepository_refresh を呼ばない Req 4_1` |
| Req 4.2 | `submit 失敗 ネットワーク時は SubscriptionRepository_refresh を呼ばない Req 4_2` |
| Req 4.3 | `submit 応答待機中に close が呼ばれても refresh は呼ばれない Req 4_3` |
| NFR 1.1 | `submit 成功時に SubscriptionRepository_refresh が 1 回呼ばれる Req 1_1`（200 ms は同期的に呼び出すロジック上自明）。Dispatchers.setMain で 同期化済み |
| NFR 1.2 | `submit 成功時に RegistrationSucceeded を refresh より先に emit する Req 1_3 NFR 1_2` |
| NFR 2.1 | コードレビューで担保（`SubscriptionRepository` interface は無変更、`Subscription` 型も無変更、`feature/registerfeed` と `shell` のみ最小変更） |
| NFR 2.2 | コードレビューで担保（登録シートの UI / バリデーション / メッセージング / トースト文言は無変更） |
| NFR 3.1 | `RegisterFeedViewModelTest` で `FakeSubscriptionRepository` を注入して検証 |
| NFR 3.2 | Req 4.1 / 4.2 / 4.3 の 3 テスト群が該当 |

## 変更ファイル一覧

- `app/src/main/kotlin/com/feedman/android/feature/registerfeed/RegisterFeedUiState.kt`
  - `RegisterFeedEvent.SubscriptionRefreshFailed` を追加
- `app/src/main/kotlin/com/feedman/android/feature/registerfeed/RegisterFeedViewModel.kt`
  - コンストラクタに `SubscriptionRepository` を追加
  - 成功パスで `triggerSubscriptionRefresh()` を呼ぶ
  - `triggerSubscriptionRefresh()` 内で `refresh()` + `observeLoadState().first()` を実行
- `app/src/main/kotlin/com/feedman/android/feature/registerfeed/RegisterFeedSheet.kt`
  - `onSubscriptionRefreshFailed` パラメータを追加（既定 `{}`、UI 側で snackbar に委譲）
- `app/src/main/kotlin/com/feedman/android/shell/AppShell.kt`
  - `RegisterFeedSheet` 呼び出しに `onSubscriptionRefreshFailed` を配線
- `app/src/main/res/values/strings.xml`
  - `register_feed_subscription_refresh_failed` snackbar 文言を追加
- `app/src/test/kotlin/com/feedman/android/feature/registerfeed/RegisterFeedViewModelTest.kt`
  - `FakeSubscriptionRepository` テストダブルを追加
  - `newViewModel` ヘルパーで 2 arg コンストラクタに集約
  - Issue #45 用テストを 6 件追加

## 確認事項（レビュワー向け）

- **Req 2.1 / 2.2 / 2.3 の動作確認**: 「登録直後にドロワーで新フィードをタップ → 記事一覧が
  読み込まれる」というユーザー動線は、`SubscriptionRepository.observeSubscriptions()` が
  新フィードを含むリストを流し、drawer がそれを反映し、ユーザーがタップすると既存の
  `feed/{feedId}` ルートで `FeedScreen` が `observeFeed(feedId)` 経由で当該購読を取得して
  記事一覧を読み込む、という既存挙動の連鎖で成立する。本 Issue では `Navigation` /
  `FeedScreen` / `SubscriptionRepository` の変更を行っていないため、新たな単体テストは
  追加していない。実機（または mock mode）での E2E 確認はレビュワーに委ねる
- **snackbar 文言の文言調整**: `register_feed_subscription_refresh_failed` の文言案を
  「フィード一覧の更新に失敗しました。ドロワーを開き直すと再試行できます」としたが、
  既存の `timeline_refresh_error`（"最新の取得に失敗しました"）等とのトーン整合を見て
  調整余地あり
- **`observeLoadState().first()` のタイミング保証**: `SubscriptionRepositoryImpl.refresh()`
  は `refreshMutex.withLock` 内で `_loadState.value` を Success / Error に確定してから戻る
  ため、refresh の suspend 戻り直後に `observeLoadState().first()` を読めば確定値が取れる
  契約と理解している。Fake では `_loadState.value = refreshResultState` で同等の挙動を
  再現している
- **Req 1.3 の文言「いずれかの失敗・遅延が他方を抑制しないかたちで両方実行する」**:
  本実装では「refresh 失敗で登録成功フィードバックを抑止しない」「登録成功 emit を
  refresh 完了より先に行う」の 2 点で担保した。逆方向（成功 emit の失敗で refresh が
  抑止されない）は `_events.emit` の `MutableSharedFlow` がブロックしない契約（buffer に
  乗らないと drop だが throw しない）から自然に成立する

## SPEC.md / SERVER.md との整合

- SPEC §5.5（フィード登録）と §4.2（購読一覧）の挙動を変更していない。両者を繋ぐ
  ViewModel 層のワイヤリング追加に閉じている

STATUS: complete
