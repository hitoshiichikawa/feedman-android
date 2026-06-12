# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-44-impl-feed-registration-sheet
- HEAD commit: fdb520a
- Compared to: origin/main..HEAD
- 変更ファイル: `core/data/FeedRegistrationRepository.kt` (新規), `di/RepositoryModule.kt`,
  `feature/registerfeed/*` (新規 4 ファイル), `shell/AppShell.kt`, `res/values/strings.xml`,
  `app/src/test/` 配下に単体テスト 4 ファイル
- 実行確認: `./gradlew :app:testDebugUnitTest --tests "...registerfeed.*" "...FeedRegistrationRepositoryImplTest"` → BUILD SUCCESSFUL（31 件 pass）

## Verified Requirements

- 1.1 — `RegisterFeedViewModel.open()` / `RegisterFeedSheet` の Visible レンダリング、`AppShell` ドロワー `onAddFeedTap` から `registerFeedViewModel.open()` 直結（`RegisterFeedViewModelTest.open で Visible 状態にする`）
- 1.2 — `register_feed_url_placeholder` / `register_feed_url_helper` を Composable で参照
- 1.3 — `RegisterFeedViewModel.updateUrl()` + `Visible.canSubmit`（`RegisterFeedViewModelTest.updateUrl で入力値が保持され canSubmit が更新される`）
- 1.4 — `Visible.canSubmit` の trim 判定（`RegisterFeedUiStateTest.url 空 / 空白のみ` 2 件）
- 1.5 — `viewModel::close` を `FeedmanSheet.onDismissRequest` / Header IconButton / `BackHandler` に結線、`close()` で `Hidden` に遷移（`RegisterFeedViewModelTest.close で Hidden に戻る`）
- 2.1 — `UrlValidation.validate(trimmed)` で http/https 以外を拒否し送信抑止（`submit 時に javascript スキーム はクライアントエラーで送信されない`）
- 2.2 — クライアントエラー時も `Visible` を維持し `url` 値を保持（同テスト）
- 2.3 — `updateUrl` 内で `clientErrorMessage`/`serverErrorMessage` を null クリア（`updateUrl でクライアントエラーとサーバーエラーが両方クリアされる`）
- 2.4 — `current.url.trim()` を validate/送信対象に使用（`submit 時に入力前後の空白は除去された値が送信される`）
- 3.1 — `repository.register(trimmed)` 呼び出し + MockWebServer で POST /api/feeds と JSON body を確認（`Req 3_1 register で api feeds エンドポイント...`）
- 3.2 — `submitInProgress = true` 遷移 + `canSubmit` 抑止 + 再 submit no-op（`submit で submitInProgress が true になり再 submit は no-op`）
- 3.3 — `OutlinedTextField.enabled = !state.submitInProgress`
- 3.4 — `close()` で Hidden 遷移、SharedFlow replay=0 で events 流出が UI に届かない設計
- 4.1 — 成功時 `_events.emit(RegistrationSucceeded)` → `close()`（`submit 成功で RegistrationSucceeded が流れシートが閉じる`）
- 4.2 — `AppShell` で `FeedmanSnackbar.show(shellSnackbarHostState, registerSucceededMessage)`
- 4.3 — 成功で `Hidden` 遷移、次回 `open()` は `Visible(url = "")` で再構築（同テストの末尾アサート）
- 5.1 — 409 + httpStatus 主導分岐 + `errorMessage.ifBlank { texts.duplicate }`（resolver 2 件 + VM 1 件）
- 5.2 — 400 / 422 で同様（resolver 2 件 + VM 1 件）
- 5.3 — 429 + `retryAfterSeconds > 0` で `texts.rateLimitWithSeconds(seconds)`（resolver 1 件 + VM 1 件 + repository 1 件）
- 5.4 — 429 + `retryAfterSeconds == null || == 0` で `texts.rateLimitGeneric`（resolver 2 件 + VM 1 件）
- 5.5 — その他で `errorMessage.ifBlank { texts.genericFallback }`（resolver 2 件 + VM 1 件）
- 5.6 — `code == NETWORK_ERROR` で `texts.networkUnreachable`（resolver 1 件 + VM 1 件 + repository 1 件）
- 5.7 — `handleServerError` で `submitInProgress = false` に戻す（`submit 409 で...` 内 `assertFalse(s.submitInProgress)`）
- 5.8 — 5.1 と同じテストでクリアを確認
- 6.1 — `Visible.url` 1 文字列のみ保持、登録結果のリストを ViewModel が持たない（設計）
- 6.2 — `FeedRegistrationRepositoryImpl` は `SubscriptionRepository` を触らない（差分 grep で確認）
- NFR 1.1 — `submit()` 内で `_uiState.value` を同期設定（即時）
- NFR 1.2 — OkHttp 既定タイムアウト 10 秒で `SocketTimeoutException` → NETWORK_ERROR、30 秒以内に到達（既存 `ApiClientFactory`）
- NFR 1.3 — `ModalBottomSheet` のドラッグ下げ / スクリムは常時受け付け
- NFR 2.1 — `register_feed_url_label` / `register_feed_close_description` / `register_feed_submit_button` 提供、`semantics { contentDescription = labelText }` で結線
- NFR 2.2 — `OutlinedTextField.isError = true` + 直下に error 色 Text
- NFR 2.3 — `FeedmanSheet` + ヘッダ + 入力欄 + 主ボタン構成（`design/mobile/fm-sheets.jsx` 準拠）
- NFR 3.1 — 全文言を `strings.xml` で日本語提供
- NFR 3.2 — `Text` で素文字列をレンダリング（Markdown/HTML パーサーなし）

## Findings

なし

## Summary

`requirements.md` の全 numeric AC（1.1〜6.2 + NFR 1.1〜3.2）に対応する実装またはテストを
確認した。Boundary 制約（`feature/registerfeed/` / `core/data` 最小 / shell 結線 /
`strings.xml` / `app/src/test/`）に逸脱なし。#45 スコープの `SubscriptionRepository` /
feed/subscriptionlist には触れていない。エラー code 文字列が SPEC 未列挙のため httpStatus
主導分岐とした設計判断は `impl-notes.md` の「設計判断と Open Questions への解釈」に記録
されており、Open Questions の方針と整合している。`./gradlew :app:testDebugUnitTest` は
31 件 pass で BUILD SUCCESSFUL。

RESULT: approve
