# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-43-impl-subscription-settings-sheet
- HEAD commit: e355be7
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `AppShell.kt` `onSelectFeedSettings = { row -> subscriptionSettingsViewModel.open(row.feedId); drawer close }` の結線。VM テスト `open で対象 feedId の Subscription を Visible 状態にする_Req 1_1`
- 1.2 — `FeedScreen.kt` フィルタ行右の `Settings` IconButton + `onOpenSettings(viewModel.feedId)`、`Navigation.kt` で `onOpenSubscriptionSettings` を伝搬し AppShell で同一 VM を共有
- 1.3 — `SubscriptionSettingsSheet.kt` `SubscriptionSettingsHeader` で favicon / feedTitle / unreadCount を表示。`Visible.subscription` は observeFeed 経由で最新化
- 1.4 — `BackHandler` + `FeedmanSheet.onDismissRequest = viewModel::close` + Header の Close IconButton。VM テスト `初期状態は Hidden_Req 1_4` / `open 後に close で Hidden に戻る_Req 1_4`
- 2.1 — `SubscriptionSettingsUiState.ALLOWED_INTERVAL_MINUTES = listOf(30, 60, 180, 360)` を `FetchIntervalSection` が forEach（15 分廃止済）
- 2.2 — `resolveInitialSelection(current)` が 30/60/180/360 のいずれかなら現在値を選択。VM テスト `現在の interval が 30 60 180 360 のいずれかなら初期選択される_Req 2_2`
- 2.3 — 4 値以外なら `null`（未選択）。`canSave` が `selectedIntervalMinutes != null` を要求するため保存ボタンは押下不可。VM テスト `現在の interval が 4 値以外なら未選択 null になる_Req 2_3` / `selectInterval で選択値が更新される 4 値以外は無視`
- 2.4 — `SubscriptionSettingsViewModel.save` が `repository.updateSettings(id, minutes)` 呼出後 `SettingsSaved` イベント emit + `close()`。Sheet 側 LaunchedEffect で snackbar 表示。Repo テスト `Issue43 Req 2_4 updateSettings で api subscriptions id settings を PUT する fetch_interval_minutes ボディ` で PUT パス + body 検証。VM テスト `save 成功で SettingsSaved イベントが流れシートが閉じる_Req 2_4`
- 2.5 — `save` 開始時 `saveInProgress=true` を同期的に更新、`canSave` ガードと `if (!current.canSave) return`。VM テスト `save 進行中は追加 save を受け付けない_Req 2_5`
- 2.6 — 失敗で `selectedIntervalMinutes` を `resolveInitialSelection(previousInterval)` に戻し errorMessage 設定、シート維持。VM テスト `save 失敗で旧値ロールバック + エラーメッセージ表示 シートは閉じない_Req 2_6 Req 5_2` / Repo テスト `Issue43 Req 5_1 updateSettings 失敗時は例外を伝搬し購読リストを変えない`
- 3.1 — `Visible.showResumeAction` が `feedStatus == stopped|error` のみ true。`ResumeStatusSection` が status / errorMessage / Resume ボタンを表示。VM テスト `resume showResumeAction は stopped と error 状態でのみ true_Req 3_1 Req 3_4`
- 3.2 — `resume()` が `repository.resume(subscription.id)` を呼ぶ。既存 Issue #41 Repo テスト + VM テスト `resume 成功で ResumeSucceeded イベントが流れシートは開いたまま_Req 3_3` で送信を担保
- 3.3 — `ResumeSucceeded` イベント emit + snackbar 表示。`observeFeed` 経由で active 化が VM の subscription に反映（Repo 内部で `_subscriptions` 更新）
- 3.4 — `showResumeAction` が active で false（`ResumeStatusSection` 非表示）。VM テスト 3.1 と共通
- 3.5 — 失敗で errorMessage のみ設定、状態表示は据置。VM テスト `resume 失敗でエラーメッセージのみ表示 状態は変更されない_Req 3_5`
- 4.1 — `requestUnsubscribe()` で `confirmUnsubscribeOpen=true`、`UnsubscribeConfirmDialog` 表示。VM テスト `requestUnsubscribe で確認ダイアログが開く_Req 4_1` / `confirmUnsubscribe 確認ダイアログ未表示のとき no-op になり DELETE を送らない_Req 4_1`
- 4.2 — `cancelUnsubscribe()` でダイアログ閉のみ。VM テスト `cancelUnsubscribe で確認ダイアログを閉じる 解除リクエストは送らない_Req 4_2`
- 4.3 — `confirmUnsubscribe()` が `repository.unsubscribe(id)` を呼ぶ。Repo テスト `Issue43 Req 4_3 unsubscribe で api subscriptions id を DELETE する` で DELETE パス検証
- 4.4 — `_subscriptions.update { filterNot { id == subscriptionId } }` でドロワー観測 Flow に新リストが流れる。Repo テスト `Issue43 Req 4_4 unsubscribe 成功で観測中のリストから当該フィードが除去される`。VM 側は `Unsubscribed` イベント + `close()`
- 4.5 — `AppShell.SubscriptionSettingsSheet.onUnsubscribed` が `currentBackStackEntry.arguments[ARG_FEED_ID]` と比較し `Timeline` へ `navigate` + `launchSingleTop`。VM テスト `confirmUnsubscribe 成功で Unsubscribed イベント feedId 付きが流れシートが閉じる_Req 4_3 Req 4_4 Req 4_5` でイベントに feedId が乗ることを担保
- 4.6 — `confirmUnsubscribe` 冒頭の `if (current.unsubscribeInProgress || current.saveInProgress) return` ガード。`save` 側も `canSave` 経由で `unsubscribeInProgress` を見る
- 4.7 — 失敗で errorMessage 設定のみ、`_subscriptions` 不変。VM テスト `confirmUnsubscribe 失敗で エラーメッセージ表示 シートは開いたまま_Req 4_7` / Repo テスト `Issue43 Req 4_7 unsubscribe 失敗時は例外を伝搬しリストを変えない`
- 5.1 — `resolveMessage(e) = e.errorMessage.ifBlank { FALLBACK_* }`。2.6 / 3.5 / 4.7 テストで間接担保（errorMessage 非 null）
- 5.2 — `save` の rollback で `selectedIntervalMinutes` を旧値に戻す。VM テスト `save 失敗で旧値ロールバック + エラーメッセージ表示 シートは閉じない_Req 2_6 Req 5_2`
- 5.3 — `handleFailureOrUnauthorized` が `code == "UNAUTHORIZED"` のとき `UnauthorizedRedirect` emit + `close()`。VM テスト `save 中に 401 が出ると UnauthorizedRedirect が流れシートが閉じる_Req 5_3`
- NFR 1.1 — `saveInProgress` / `resumeInProgress` / `unsubscribeInProgress` は state 直前同期更新（同 frame で反映、100ms 余裕）
- NFR 1.2 — StateFlow / SharedFlow の emit を coroutine 完了直後に行うため 500ms 余裕
- NFR 2.1 — `feedmanDimens.minTapTarget` で IconButton / TextButton / Button / IntervalSegment を 44dp 確保。`FeedmanSheet` が ModalBottomSheet（高さ 2/3〜）
- NFR 3.1 — `IntervalSegment` の `Modifier.semantics { selected = ...; contentDescription = label }`
- NFR 3.2 — `AlertDialog.onDismissRequest = onCancel`（Material3 既定で戻る + スクリム外側タップに対応）

## Findings

なし

## Summary

Issue #43 の全 AC（Req 1.x〜5.x + NFR 1〜3）について、`feature/subscriptionsettings` 配下の VM/UI、`core/data` の Repository 拡張（PUT settings / DELETE）、`shell/AppShell` + `Navigation` + `feature/feed/FeedScreen` 結線、`strings.xml` の文言追加で観測可能な実装が揃い、JVM 単体テスト（VM: 22 件 / Repo: Issue43 関連 4 件）が `./gradlew :app:testDebugUnitTest` で green。間隔セグメントは 30/60/180/360 分の 4 値で確定済み（15 分廃止反映済）、保存 PUT・解除 DELETE・解除後の画面退避（`AppShell.SubscriptionSettingsSheet.onUnsubscribed`）・失敗時の旧値保持と認証切れ誘導もすべてカバーされている。境界（feature/subscriptionsettings / core/data 最小 / shell・feed 結線 / strings.xml / app/src/test）も逸脱なし。

RESULT: approve
