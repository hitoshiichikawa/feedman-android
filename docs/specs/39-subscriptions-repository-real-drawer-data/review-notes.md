# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-39-impl-subscriptions-real-data
- HEAD commit: a073b74
- Compared to: origin/main..HEAD
- Feature Flag Protocol: opt-out（採否確認結果。flag 観点の細目は適用しない）

## Verified Requirements

- 1.1 — `SubscriptionRepositoryImpl.refresh()` が `api.getSubscriptions()` を呼び出し、`DrawerViewModel.init` が `viewModelScope.launch { repository.refresh() }` で起動 / テスト: `SubscriptionRepositoryImplTest#Req 1_1 refresh で api subscriptions エンドポイントを GET する` + `DrawerViewModelTest#Req 1_1 ViewModel 初期化時にリポジトリの refresh が起動される`
- 1.2 — 200 応答を `List<Subscription>` として decode し `_subscriptions` に流す / テスト: `SubscriptionRepositoryImplTest#Req 1_2 1_3 200 応答を Subscription 配列として decode し observe へ流す`
- 1.3 — `feed_id` / `feed_title` / `favicon_url` / `unread_count` / `feed_status` の各フィールドを assert で検証 / 同上テスト内
- 1.4 — `_subscriptions.value = fetched` で順序維持（並び替え無し）/ テスト: `Req 1_4 サーバーが返した順序を変更せずそのまま流す`
- 1.5 — 空配列レスポンスで空リスト＋Success に遷移 / テスト: `Req 1_5 空配列のとき空のフィードリストを流す`
- 1.1.1 — `DrawerViewModel.uiState` を `combine` + `stateIn` で公開、`StateFlow` 経由で自動反映 / テスト: `リポジトリが新しいリストを emit すると uiState に反映される_NFR 2_1`
- 1.1.2 — `DrawerFeedRow.from(subscription)` で `unread_count` を経由（#30 から変更なし） / 既存 `DrawerFeedRowTest`
- 1.1.3 — `feed_status` 経由で `statusIcon` を判定（#30 から変更なし） / 既存 `DrawerFeedRowTest`
- 1.1.4 — `data:` URL の decode を `subscription_active.json` フィクスチャで検証 / `Req 1_2 1_3` の assert `assertTrue(items[0].faviconUrl?.startsWith("data:") == true)`
- 1.1.5 — `favicon_url` null 時の letter avatar fallback は #26 で導入済の Favicon Composable 経路を再利用（本 Issue で変更なし）
- 2.1 — `_loadState` を `Error(message, code)` に遷移 / テスト: `SubscriptionRepositoryImplTest#Req 2_1 2_6 非 2xx で SPEC エラー応答が来たら Error 状態で message と code を通知する`, `Req 2_1 ネットワーク失敗時に Error 状態で通知する code は NETWORK_ERROR`, `DrawerViewModelTest#Req 2_1 2_2 取得失敗時に feedSection が Error message を保持する`
- 2.2 — `DrawerFeedsSection` 内に `DrawerFeedSectionError` Composable を配置（`DrawerContent.kt:258-261`）、`FeedSectionState.Error` から message を伝達 / `FeedSectionStateTest#Error のとき rows の有無にかかわらず Error を返し message を保持する_Req 2_1_2_2_2_6`
- 2.3 — `DrawerContent.kt:128-147` の構造により、`drawerMainItems`（メイン項目）/ `drawerFooterItems`（フッタ）/ ヘッダは `DrawerFeedsSection` の外側で常時描画される。Loading / Error 表示は `DrawerFeedsSection` 内のみで分岐
- 2.4 — `onRetry → onRetryLoadFeeds → viewModel.retryLoadSubscriptions() → repository.refresh()` / テスト: `SubscriptionRepositoryImplTest#Req 2_4 再試行 refresh で 2 回目の取得が走り Success に回復する`, `DrawerViewModelTest#Req 2_4 retryLoadSubscriptions が repository の refresh を再呼び出しする`
- 2.5 — `_loadState` の `Loading` 遷移 + `FeedSectionState.from` で `hasRows=false` のとき `Loading` 返却 / テスト: `SubscriptionRepositoryImplTest#Req 2_5 refresh 中は Loading 状態を観測者へ通知する`, `DrawerViewModelTest#Req 2_5 取得中 rows 空 のとき feedSection が Loading になる`, `FeedSectionStateTest#Loading かつ rows が空のとき Loading を返す_Req 2_5`
- 2.6 — `FeedmanException.errorMessage` を `SubscriptionLoadState.Error.message` に転写し、UI 側まで貫通 / テスト `Req 2_1 2_6` で assert `assertEquals("リクエストパラメータが不正です。", error.message)`
- 3.1 — `selectSubscriptionRepository(mockMode=true) → fake` の純粋関数 / テスト: `SubscriptionRepositoryProviderTest#Req 3_1 mockMode true のとき Fake 実装を返す`
- 3.2 — `FakeSubscriptionRepository.refresh()` が no-op（API を呼ばない）、Retrofit 依存なし / コード review + テスト間接保証
- 3.3 — `selectSubscriptionRepository(mockMode=false) → real` / テスト: `Req 3_3 mockMode false のとき実 API 実装を返す`
- 3.4 — Fake / Real が同一 `SubscriptionRepository` interface を実装（コンパイル保証）。`DrawerViewModelTest` 各テストが両系統で動く構造
- 4.1 — `ApiClientFactory.create` 経路を経由するため、共通 authenticator の結果を透過する設計（本実装は throw された結果をそのまま受ける）
- 4.2 — 共通層が再認証成功で 2xx を返した場合は Req 1.2 と同一経路で `Success` に遷移
- 4.3 — 401 継続時に `code = "UNAUTHORIZED"` の Error 状態を観測者へ通知 / テスト: `SubscriptionRepositoryImplTest#Req 4_3 401 が継続したら識別可能な Error 状態として通知する`
- NFR 1.1 — `git diff --name-only origin/main..HEAD` で `core/data` / `di` / `shell` / `app/src/test` / `docs/specs` のみが変更対象。`core/network` / `core/model` / `feature/*` / `strings.xml` 無変更を確認
- NFR 1.2 — `SubscriptionRepository` interface に追加された 2 メソッド（`observeLoadState` / `refresh`）は Fake にも実装され、既存 `DrawerViewModel` 利用箇所（`AppShell.feedTitleLookup` 等）は機械的書き換えなしに動作。既存テスト pass
- NFR 2.1 — MockWebServer で HTTP 層をモックし正常系・失敗系・空応答を `SubscriptionRepositoryImplTest` で検証
- NFR 2.2 — `DrawerViewModelTest` で stub repository を注入し、`FeedSectionStateTest` で純粋関数を検証
- NFR 2.3 — `selectSubscriptionRepository` 純粋関数で DI 切替を単一箇所に集約
- NFR 3.1 — `DrawerContent` の構造解析でフィードセクション外（メイン項目・フッタ・ヘッダ）が独立して描画されることを確認
- NFR 3.2 — `viewModelScope.launch { repository.refresh() }` 起動のため UI スレッドはブロックされず、他のドロワー操作は通常通り動作

## Findings

なし。

## Summary

最新 commit 群（6 commits）は requirements.md の全 numeric ID（1.x, 1.1.x, 2.x, 3.x, 4.x, NFR 1.x/2.x/3.x）について、実装またはテストで AC を裏付けている。変更範囲は NFR 1.1 が要求する `core/data` / `di` / `shell` / テストに閉じ、#42/#43 が扱う購読変更操作（DELETE / PUT settings / POST resume / POST fetch）には踏み込んでいない。テスト規約（MockWebServer 使用、命名規則、Arrange/Act/Assert）も遵守されており、`./gradlew :app:testDebugUnitTest` で全テスト pass を確認した。Composable 描画検証は instrumented 領分として JVM テスト不在を missing test 扱いせず、`FeedSectionStateTest` で純粋関数の状態導出を検証している点も妥当。

RESULT: approve
