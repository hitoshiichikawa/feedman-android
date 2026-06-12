# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-45-impl-refresh-subscriptions
- HEAD commit: 7f1d13a
- Compared to: origin/main..HEAD
- 対象 commits:
  - c7d0824 docs(spec): Issue #45 登録後の購読再読込の要件定義を追加
  - 2f7a496 feat(registerfeed): 登録成功後に購読一覧を自動再取得する (#45)
  - 7f1d13a docs(spec): Issue #45 実装ノートを追加

## Verified Requirements

- 1.1 — `RegisterFeedViewModel.submit()` 成功パスで `triggerSubscriptionRefresh()` →
  `subscriptionRepository.refresh()` を 1 回呼ぶ実装。テスト
  `submit 成功時に SubscriptionRepository_refresh が 1 回呼ばれる Req 1_1` で
  `refreshCalls == 1` を検証
- 1.2 — drawer 観測経路は既存（`SubscriptionRepository.observeSubscriptions()` →
  drawer）。本 PR で変更していない。impl-notes.md の対応表で既存挙動依存である旨が
  明示されている
- 1.3 — `RegistrationSucceeded` を emit + `close()` の **後** に `refresh` を起動する順序。
  テスト `submit 成功時に RegistrationSucceeded を refresh より先に emit する Req 1_3 NFR 1_2`
  で `CompletableDeferred` を使い refresh suspend 中でも `RegistrationSucceeded` が
  awaitItem できることを確認。refresh 失敗時に `RegistrationSucceeded` が抑止されない
  ことは `submit 成功後の refresh 失敗時に SubscriptionRefreshFailed が emit され シートは閉じたまま Req 3_1 Req 3_2 Req 3_3`
  で確認
- 1.4 — `submit()` 内で同期的（同一 launch スコープ内）に `refresh` を呼ぶため追加操作不要。
  Req 1.1 と同じテストでカバー
- 2.1 / 2.2 / 2.3 — 既存の Navigation / FeedScreen / SubscriptionRepository は変更されておらず、
  drawer タップ → `feed/{feedId}` ルートで `observeFeed(feedId)` から取得する既存連鎖で
  自然に成立する。impl-notes.md「確認事項」に E2E 動作確認をレビュワー / 実機に委ねる
  旨が明示されている
- 3.1 — 成功フィードバック（`RegistrationSucceeded` emit + `close()`）が refresh の結果に
  関わらず先行する実装。`submit 成功後の refresh 失敗時に SubscriptionRefreshFailed が emit され シートは閉じたまま Req 3_1 Req 3_2 Req 3_3`
  で `RegistrationSucceeded` → `SubscriptionRefreshFailed` の順で両方届くことを検証
- 3.2 — refresh 失敗時も `RegisterFeedUiState` は `Hidden` のままで、モーダル / 全画面エラーへ
  遷移しない。同テストで `Hidden` 維持を assert。AppShell 側も `FeedmanSnackbar.show(...)`
  という非ブロッキング snackbar 表示のみ
- 3.3 — `RegisterFeedEvent.SubscriptionRefreshFailed` → AppShell の
  `onSubscriptionRefreshFailed` → `register_feed_subscription_refresh_failed` snackbar の
  経路でユーザー視認可能なエラー提示を行う。drawer 側のフィードセクションエラー表示は
  #39 で既に実装済みの `observeLoadState()` 経路で独立して反映される
- 3.4 / 3.5 — `SubscriptionRepository` の振る舞いは無変更で、既存復帰経路（#39 Requirement 2.4 /
  drawer 再オープン）がそのまま機能する。impl-notes.md 対応表で既存挙動依存である旨を明示
- 4.1 — `repository.register(trimmed)` 直後の `catch (e: FeedmanException)` 節は
  `handleServerError(e)` のみ呼び、`triggerSubscriptionRefresh()` は呼ばない構造。テスト
  `submit 失敗 4xx 時は SubscriptionRepository_refresh を呼ばない Req 4_1` で
  `refreshCalls == 0` を検証
- 4.2 — `catch (e: Exception)` 節も `triggerSubscriptionRefresh()` を呼ばない。テスト
  `submit 失敗 ネットワーク時は SubscriptionRepository_refresh を呼ばない Req 4_2`
  でカバー
- 4.3 — register が応答待機中（suspend 中）に `close()` された場合、`RegistrationSucceeded`
  emit に到達しないため refresh も呼ばれない。テスト
  `submit 応答待機中に close が呼ばれても refresh は呼ばれない Req 4_3` で確認
- NFR 1.1 — refresh 呼び出しは同期的に submit 成功直後の同一 coroutine launch 内で実行されるため
  200ms 制約は同期構造から自明。Req 1.1 テストで担保
- NFR 1.2 — Req 1.3 テストで `CompletableDeferred` を使った順序保証を確認
- NFR 2.1 — diff 確認: `SubscriptionRepository` interface・`Subscription` 型・
  `feature/*` の他画面は無変更。変更は `feature/registerfeed` 内 3 ファイル + `shell/AppShell.kt`
  の最小配線 + `strings.xml` のみ
- NFR 2.2 — 登録シートの UI / バリデーション / エラーメッセージング / 登録成功トースト文言は
  diff 上で変更されていない（追加文言は `register_feed_subscription_refresh_failed`
  snackbar 用のみ）
- NFR 3.1 / 3.2 — `FakeSubscriptionRepository` テストダブルを差し替えて 7 テスト追加
  （成功 1 / 順序 1 / 4xx 1 / ネットワーク 1 / close 1 / refresh 失敗 1 / refresh 成功 1）

## テスト実行結果

`./gradlew :app:testDebugUnitTest --tests "com.feedman.android.feature.registerfeed.RegisterFeedViewModelTest"` を実行し、
BUILD SUCCESSFUL を確認。impl-notes.md 記載のテスト 6 件追加 + 既存テスト維持で全 green。

## 境界（_Boundary:_）の確認

NFR 2.1 / 2.2 で宣言された境界（`feature/registerfeed` および `core/data` の既存
`SubscriptionRepository.refresh()` 利用のみ）に閉じている。実際の差分:

- `feature/registerfeed/RegisterFeedViewModel.kt` — `SubscriptionRepository` 注入と
  `triggerSubscriptionRefresh()` 追加（境界内）
- `feature/registerfeed/RegisterFeedUiState.kt` — `SubscriptionRefreshFailed` イベント追加（境界内）
- `feature/registerfeed/RegisterFeedSheet.kt` — `onSubscriptionRefreshFailed` パラメータ追加（境界内）
- `shell/AppShell.kt` — `RegisterFeedSheet` 呼び出し側で snackbar 配線のみ。これは
  Req 3.3 の「ユーザー視認可能な形での提示」を担う最小限の shell 側変更であり、既存の
  `onRegistrationSucceeded` 配線とパラレルな最小付加。仕様の NFR 2.1 文面は
  `feature/*` の他画面ソースを変更しないとしており、`shell` への最小配線は明示的に
  禁じられていないため許容と判断
- `app/src/main/res/values/strings.xml` — snackbar 文言 1 件追加のみ。既存文言は無変更
- `app/src/test/...RegisterFeedViewModelTest.kt` — テストのみ

`SubscriptionRepository` interface / `Subscription` モデル / `feature/*` の他画面 / drawer 側
コードは無変更。境界逸脱なし。

## Findings

なし

## Summary

requirements.md の全 numeric ID（Req 1.1〜4.3、NFR 1.1〜3.2）について、新規追加実装 / 新規
テストでカバーされる項目と、既存挙動に依存することを impl-notes.md の対応表で明示する
項目の双方が整合している。境界逸脱なし、テスト green。

RESULT: approve
