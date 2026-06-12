# Issue #43 購読設定シート 実装メモ

## 受入基準 ↔ テスト対応表

| Req ID | 担保するテスト / 実装 |
|---|---|
| 1.1 ドロワーから設定シートを開く | `AppShell.kt` の `onSelectFeedSettings = { row -> subscriptionSettingsViewModel.open(row.feedId) ... }` 結線（既存 `DrawerFeedRowTest` で設定アイコンの存在 + IconButton 配線は担保済み）。`SubscriptionSettingsViewModelTest.open で対象 feedId の Subscription を Visible 状態にする_Req 1_1` |
| 1.2 フィード別画面から設定シートを開く | `FeedScreen.kt` のフィルタ右に追加した歯車 `IconButton` + `onOpenSettings(viewModel.feedId)` 結線（`Navigation.kt` 経由）。VM テストは上記 1.1 と共通 |
| 1.3 タイトル / favicon / 未読件数を表示 | `SubscriptionSettingsSheet.kt` の `SubscriptionSettingsHeader`。`SubscriptionSettingsViewModelTest.open で対象 feedId の Subscription を Visible 状態にする_Req 1_1`（state.subscription にメタが入る） |
| 1.4 クローズ操作で閉じる | `SubscriptionSettingsViewModelTest.open 後に close で Hidden に戻る_Req 1_4` / `SubscriptionSettingsViewModelTest.初期状態は Hidden_Req 1_4`。`BackHandler` + `FeedmanSheet.onDismissRequest` で結線 |
| 2.1 30/60/180/360 分の 4 値のみ表示 | `SubscriptionSettingsUiState.ALLOWED_INTERVAL_MINUTES = listOf(30, 60, 180, 360)` + `FetchIntervalSection` の `forEach`。`SubscriptionSettingsViewModelTest.selectInterval で選択値が更新される 4 値以外は無視`（4 値以外を無視） |
| 2.2 現在値に対応するセグメント選択 | `SubscriptionSettingsViewModelTest.現在の interval が 30 60 180 360 のいずれかなら初期選択される_Req 2_2` |
| 2.3 4 値以外なら「未選択」 + 書き込まない | `SubscriptionSettingsViewModelTest.現在の interval が 4 値以外なら未選択 null になる_Req 2_3`（canSave も false 担保） |
| 2.4 保存成功でサーバー送信 + 閉じる + 完了通知 | `SubscriptionSettingsViewModelTest.save 成功で SettingsSaved イベントが流れシートが閉じる_Req 2_4` / `SubscriptionRepositoryImplTest.Issue43 Req 2_4 updateSettings で api subscriptions id settings を PUT する fetch_interval_minutes ボディ`（PUT パス + ボディ検証） |
| 2.5 送信中は進行中状態 + 追加保存抑止 | `SubscriptionSettingsViewModelTest.save 進行中は追加 save を受け付けない_Req 2_5`（saveInProgress = true / 呼出回数据置確認） |
| 2.6 失敗で旧値ロールバック + エラー表示 | `SubscriptionSettingsViewModelTest.save 失敗で旧値ロールバック + エラーメッセージ表示 シートは閉じない_Req 2_6 Req 5_2` / `SubscriptionRepositoryImplTest.Issue43 Req 5_1 updateSettings 失敗時は例外を伝搬し購読リストを変えない`（Repository 層） |
| 3.1 stopped/error 時に状態 + 再開を表示 | `SubscriptionSettingsViewModelTest.resume showResumeAction は stopped と error 状態でのみ true_Req 3_1 Req 3_4`。UI 側は `if (state.showResumeAction)` で `ResumeStatusSection` を出し分け |
| 3.2 再開タップで再開要求送信 | `SubscriptionSettingsViewModelTest.resume 成功で ResumeSucceeded イベントが流れシートは開いたまま_Req 3_3`（`repository.resume(subscriptionId)` 呼出確認は同テスト + 既存 Issue #41 の `SubscriptionRepositoryImplTest.Issue41 Req 3_5 resume で api subscriptions id resume を POST する` を流用） |
| 3.3 成功で完了通知 + active 化 | `SubscriptionSettingsViewModelTest.resume 成功で ResumeSucceeded イベントが流れシートは開いたまま_Req 3_3`（イベント発火 / state.subscription.feedStatus は StubRepository 側で active に置換され observeFeed 経由で UI 反映） |
| 3.4 active 中は再開 / 状態バッジを非表示 | `SubscriptionSettingsViewModelTest.resume showResumeAction は stopped と error 状態でのみ true_Req 3_1 Req 3_4`（active = false 担保）。UI は同上 |
| 3.5 失敗で状態変更なし + エラー表示 | `SubscriptionSettingsViewModelTest.resume 失敗でエラーメッセージのみ表示 状態は変更されない_Req 3_5` |
| 4.1 解除タップ → 確認ダイアログ | `SubscriptionSettingsViewModelTest.requestUnsubscribe で確認ダイアログが開く_Req 4_1` / `SubscriptionSettingsViewModelTest.confirmUnsubscribe 確認ダイアログ未表示のとき no-op になり DELETE を送らない_Req 4_1` |
| 4.2 キャンセルで送信せずシート維持 | `SubscriptionSettingsViewModelTest.cancelUnsubscribe で確認ダイアログを閉じる 解除リクエストは送らない_Req 4_2` |
| 4.3 確定で解除リクエスト送信 | `SubscriptionSettingsViewModelTest.confirmUnsubscribe 成功で Unsubscribed イベント feedId 付きが流れシートが閉じる_Req 4_3 Req 4_4 Req 4_5` / `SubscriptionRepositoryImplTest.Issue43 Req 4_3 unsubscribe で api subscriptions id を DELETE する` |
| 4.4 成功で閉じる + ドロワー一覧から除去 | `SubscriptionRepositoryImplTest.Issue43 Req 4_4 unsubscribe 成功で観測中のリストから当該フィードが除去される`（Flow 経由でドロワー DrawerViewModel に自動反映） |
| 4.5 表示中フィードなら timeline へ退避 | `AppShell.kt` の `SubscriptionSettingsSheet.onUnsubscribed` が `currentBackStackEntry.arguments[ARG_FEED_ID]` と照合して `navController.navigate(Timeline)`。VM テスト `confirmUnsubscribe 成功で Unsubscribed イベント feedId 付きが流れシートが閉じる_Req 4_3 Req 4_4 Req 4_5` でイベントに feedId が乗ることを担保（NavController テストは Compose UI test 範囲のため本実装スコープでは VM 経路のみ） |
| 4.6 解除送信中は二重実行・他保存を抑止 | `SubscriptionSettingsViewModel.confirmUnsubscribe` の `if (current.unsubscribeInProgress || current.saveInProgress) return` ガード（save 側も同様）。VM テストは上記 4.3 の inProgress 遷移で間接担保 |
| 4.7 失敗でリスト・遷移変更なし + エラー表示 | `SubscriptionSettingsViewModelTest.confirmUnsubscribe 失敗で エラーメッセージ表示 シートは開いたまま_Req 4_7` / `SubscriptionRepositoryImplTest.Issue43 Req 4_7 unsubscribe 失敗時は例外を伝搬しリストを変えない` |
| 5.1 サーバー応答 / 汎用メッセージ表示 | VM の `resolveMessage(e)` が `e.errorMessage.ifBlank { ... FALLBACK ... }` を返す。テストは 2.6 / 3.5 / 4.7 で間接担保（errorMessage 非空 + 内容一致） |
| 5.2 失敗で楽観的変更をロールバック | `SubscriptionSettingsViewModelTest.save 失敗で旧値ロールバック + エラーメッセージ表示 シートは閉じない_Req 2_6 Req 5_2`（selectedIntervalMinutes が旧値に戻る） |
| 5.3 認証切れでシート閉 + ログイン誘導 | `SubscriptionSettingsViewModelTest.save 中に 401 が出ると UnauthorizedRedirect が流れシートが閉じる_Req 5_3`。AppShell 側は `SessionStateProvider` の LoggedOut で自動的にログイン画面へ切替わる（Issue #29 既存挙動を流用） |
| NFR 1.1 進行中状態 100ms 以内 | Compose の状態遷移は同 frame で反映（100ms 余裕で達成）。`saveInProgress` を `Visible.copy(saveInProgress = true)` で直前同期更新 |
| NFR 1.2 応答受信後 500ms 以内反映 | StateFlow + SharedFlow の coroutine 完了直後に emit。実測手段は CI 環境では難しいため設計レビューで担保 |
| NFR 2.1 視覚整合 / 44px タップ標的 | `Modifier.sizeIn(minWidth/minHeight = feedmanDimens.minTapTarget)` で IconButton / IntervalSegment / 保存・解除ボタンを 44dp 確保。シート高は `FeedmanSheet` の `ModalBottomSheet` 既定（skipPartiallyExpanded=false）で 2/3 〜 full のレンジ |
| NFR 3.1 セグメントに選択状態 a11y | `IntervalSegment` の `Modifier.semantics { selected = selected; contentDescription = label }` で読み上げ可能 |
| NFR 3.2 確認ダイアログを戻る / 外部タップでキャンセル | `AlertDialog.onDismissRequest = onCancel`（Material3 既定で戻る + スクリム外側タップに対応） |

## 実装上の判断

- **VM スコープ**: `SubscriptionSettingsViewModel` は `LoggedInShell` 直下で 1 つ保持。`open(feedId)` を再呼び出ししたとき、観測 Job をキャンセル → Hidden にリセット → 新 feedId 観測の流れにしている。ドロワーと FeedScreen の双方から同一 VM を共有することで、状態の二重持ちを避けた。
- **observeFeed 経由の最新化**: `subscription` の最新スナップショットは Repository の `observeFeed(feedId)` から流れる。`updateSettings` / `resume` 成功で Repository が `_subscriptions` を更新するため、VM の `Visible.subscription` は明示コピーせずとも反映される（再開で active 化、保存で interval 値更新が自動）。
- **解除後の画面退避**: 解除確定時点で `Subscription` が `_subscriptions` から消えるため、`observeFeed` は次の collect で `null` を流す。VM は `Hidden` に戻すが、`Unsubscribed` イベントを先に emit してから `close()` を呼ぶことで、UI 側は feedId を受け取れる。
- **未選択状態（Req 2.3）**: 現状サーバーが返す `fetch_interval_minutes` がほぼ 30/60/180/360 のいずれかになる前提だが、将来サーバー側で 90 分等を許容する可能性に備えて `resolveInitialSelection` を分離した。失敗ロールバック時も旧値が 4 値外なら `null` に戻る（次回保存可能になるには再度セグメント選択が必要）。
- **AppShellSheet 列挙との関係**: AppShellSheet enum（Account / FeedRegistration）には設定シートを追加しなかった。設定シートは「対象 feedId」を引数に持つため enum 値だけでは表現できず、独立した VM 状態（`Hidden / Visible(subscription, ...)`）で扱うほうが筋が良いと判断。
- **解除確認の AlertDialog 配置**: `BackHandler` をシート側に 1 つ持つ構造のため、ダイアログ表示中の戻る操作は AlertDialog 側で先に消費される（Material3 既定）。シートの BackHandler は二重発火しない。

## 確認事項（レビュワー判断ポイント）

- requirements.md は Issue #43 の design.md / tasks.md を持たない直接実装方針。設計差し戻しが必要なら本 PR の指摘に従って Architect 起動を検討。
- `AppShellSheet` enum との並びを統一すべきか（設定シートも enum に加える派 vs 独立 VM 状態派）は設計判断の余地あり。本実装は後者を採用。
- フィード別画面の設定アイコン配置（フィルタタブ行の右端）は SPEC §5.6 / プロト fm-sheets.jsx に明示位置の指定がないため、視覚整合性は要レビュー。
- Unsubscribed 時の navigate 先（timeline）は要件で「横断新着タイムライン」と明示されているため `AppRoute.Timeline.id` 一択。
- 認証切れ（UNAUTHORIZED）時の動作は VM 側でシートを閉じてイベントを流すまでで、ログイン画面への切替は既存 `SessionStateProvider` の責務。本 Issue で AuthRepository を触らない方針。

## 派生タスク候補

- 設定シートの Compose UI test（instrumented）。本 Issue では JVM 単体テストでロジックを担保したが、ボタン押下 → ダイアログ表示の UI 流入は instrumented で別 Issue に切り出す。
- `SubscriptionSettingsEvent.UnauthorizedRedirect` を受けて明示的に LoggedOut に切替えるフックは AuthRepository 連携の Issue で扱う。

## STATUS

STATUS: complete
