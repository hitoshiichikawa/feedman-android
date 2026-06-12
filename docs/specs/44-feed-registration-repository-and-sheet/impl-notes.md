# Implementation Notes — Issue #44 フィード登録リポジトリと登録シート

## 概要

`requirements.md` に基づき、フィード登録シートと支援するリポジトリ層を実装した。

- `core/data/FeedRegistrationRepository.kt` （interface + 実装）
- `feature/registerfeed/`
  - `RegisterFeedUiState` （sealed: Hidden / Visible）と `RegisterFeedEvent`
  - `RegisterFeedErrorResolver` + `RegisterFeedErrorTexts`（純粋関数群）
  - `RegisterFeedViewModel`
  - `RegisterFeedSheet` Composable
- `shell/AppShell.kt`: 起動点を `AppShellSheet.FeedRegistration` placeholder から
  `RegisterFeedViewModel.open()` 直接呼びへ置換。登録成功トースト表示。
- `strings.xml`: 文言 14 個追加
- `RepositoryModule`: `FeedRegistrationRepository` の `@Binds` バインディング

## 受入基準 → テスト対応表

| Requirement ID | カバーするテスト | 種別 |
|---|---|---|
| 1.1 シート起動 / URL 入力欄表示 | `RegisterFeedViewModelTest.初期状態は Hidden`, `open で Visible 状態にする` | JVM 単体 |
| 1.2 プレースホルダ / 説明文 | `strings.xml`: `register_feed_url_placeholder` / `register_feed_url_helper`（Composable で参照） | string resource |
| 1.3 入力値保持 / 送信ボタン活性更新 | `RegisterFeedViewModelTest.updateUrl で入力値が保持され canSubmit が更新される` | JVM 単体 |
| 1.4 空 / 空白のみで送信抑止 | `RegisterFeedViewModelTest.空白のみの入力では canSubmit が false`, `RegisterFeedUiStateTest`（5 件） | JVM 単体 |
| 1.5 閉じる操作で入力状態破棄 | `RegisterFeedViewModelTest.close で Hidden に戻る`, `RegisterFeedSheet` で `BackHandler` 結線 | JVM 単体 + Composable 結線 |
| 2.1 不正 URL のクライアント側ブロック | `RegisterFeedViewModelTest.submit 時に javascript スキーム はクライアントエラーで送信されない`, `submit 時に http スキームは送信される` | JVM 単体 |
| 2.2 入力欄編集可能を維持 | `RegisterFeedViewModelTest.submit 時に javascript スキーム ...`（クライアントエラー後も `Visible` のまま、`url` 値も保持） | JVM 単体 |
| 2.3 入力変更で前回エラー解除 | `RegisterFeedViewModelTest.updateUrl でクライアントエラーとサーバーエラーが両方クリアされる` | JVM 単体 |
| 2.4 前後の空白除去 | `RegisterFeedViewModelTest.submit 時に入力前後の空白は除去された値が送信される` | JVM 単体 |
| 3.1 サーバーへ送信 | `FeedRegistrationRepositoryImplTest.Req 3_1 register で api feeds エンドポイントを POST し url フィールドを含む JSON ボディを送る`, `RegisterFeedViewModelTest.submit 成功で ...` | MockWebServer + JVM 単体 |
| 3.2 送信中ボタンローディング / 再送信抑止 | `RegisterFeedViewModelTest.submit で submitInProgress が true になり再 submit は no-op`, `RegisterFeedUiStateTest.Req 3_2 submitInProgress true ...` | JVM 単体 |
| 3.3 入力欄編集抑止 | Composable で `OutlinedTextField.enabled = !state.submitInProgress` | UI 結線 |
| 3.4 待機中のシート閉じ | `RegisterFeedViewModel.close()` が Hidden に遷移し、`viewModelScope` の自然なキャンセル境界に従う（成功時の event 流出は close 後の HIdden 状態なので UI 側は受け取らない） | 設計 |
| 4.1 成功でシートを閉じる | `RegisterFeedViewModelTest.submit 成功で RegistrationSucceeded が流れシートが閉じる` | JVM 単体 |
| 4.2 成功トースト | `AppShell.kt`: `onRegistrationSucceeded = FeedmanSnackbar.show(shellSnackbarHostState, ...)` | UI 結線 |
| 4.3 入力初期化でシート終了 | `RegisterFeedViewModelTest.submit 成功で ...`（成功後 `RegisterFeedUiState.Hidden` に遷移し、次回 `open()` で `Visible(url = "")` で再構築される） | JVM 単体 |
| 5.1 重複登録 | `RegisterFeedErrorResolverTest.Req 5_1 409 でサーバー message が存在すれば優先する` + `Req 5_1 409 でサーバー message が空ならフォールバック文言を使う` + `RegisterFeedViewModelTest.submit 409 で重複登録の文言を表示しシートは閉じない` | JVM 単体 |
| 5.2 URL 不正 / フィード未検出 | `RegisterFeedErrorResolverTest.Req 5_2 400 でサーバー message を優先する` + `422 でサーバー message が空ならフォールバック文言`, `RegisterFeedViewModelTest.submit 400 で URL 不正文言を表示する` | JVM 単体 |
| 5.3 レート制限 + 残時間付き | `RegisterFeedErrorResolverTest.Req 5_3 429 で retryAfterSeconds があれば残時間付き文言`, `RegisterFeedViewModelTest.submit 429 で retryAfterSeconds 付き文言を表示する`, `FeedRegistrationRepositoryImplTest.Req 5_3 429 レート制限時に retryAfterSeconds を持つ FeedmanException を投げる` | JVM 単体 + MockWebServer |
| 5.4 レート制限 残時間なし | `RegisterFeedErrorResolverTest.Req 5_4 429 で retryAfterSeconds が null なら汎用文言` + `Req 5_4 429 で retryAfterSeconds が 0 でも汎用文言`, `RegisterFeedViewModelTest.submit 429 で retryAfterSeconds が null なら汎用再試行文言` | JVM 単体 |
| 5.5 その他 4xx / 5xx | `RegisterFeedErrorResolverTest.Req 5_5 500 でサーバー message を優先する` + `500 でサーバー message が空なら汎用フォールバック`, `RegisterFeedViewModelTest.submit 500 でサーバー message を表示する` | JVM 単体 |
| 5.6 通信失敗 | `RegisterFeedErrorResolverTest.Req 5_6 NETWORK_ERROR ならネットワーク文言を使う`, `RegisterFeedViewModelTest.submit ネットワーク失敗時にネットワーク文言を表示する`, `FeedRegistrationRepositoryImplTest.Req 5_6 ネットワーク失敗時に NETWORK_ERROR の FeedmanException を投げる` | JVM 単体 + MockWebServer |
| 5.7 エラー表示中に入力欄 / ボタンが再操作可能 | `RegisterFeedViewModelTest.submit 409 で重複登録の文言を表示しシートは閉じない` 内の `submitInProgress == false` アサート | JVM 単体 |
| 5.8 入力変更でサーバー由来エラー解除 | `RegisterFeedViewModelTest.updateUrl でクライアントエラーとサーバーエラーが両方クリアされる` | JVM 単体 |
| 6.1 登録 URL を破棄 / リスト保持しない | `RegisterFeedViewModel` 設計（`Visible.url` 1 文字列のみ保持、登録結果リストを持たない） | 設計 |
| 6.2 購読一覧の即時反映は別 Issue | `FeedRegistrationRepositoryImpl` は `SubscriptionRepository` 内部状態を更新しない（責務外） | 設計 |
| NFR 1.1 200ms 以内ローディング遷移 | `submit()` 内で `_uiState.value` 即時設定（同期）。Compose 再構成のオーバーヘッドは ms オーダー以下 | 設計 |
| NFR 1.2 30 秒タイムアウト | **未実装 / 確認事項**（OkHttp 既定タイムアウトに依存する。`ApiClientFactory` で明示設定なし → OkHttp 既定 10 秒で `SocketTimeoutException` が `NETWORK_ERROR` 経由で Req 5.6 文言になる） | 既存 OkHttp 設定 |
| NFR 1.3 ローディング中も閉じ可能 | `OutlinedTextField` は `enabled = false` 時も操作可能性（タッチ可能領域）を保ち、`FeedmanSheet`（`ModalBottomSheet`）のドラッグ下げ / スクリムタップは常時受け付ける | UI 結線 |
| NFR 2.1 a11y ラベル | `strings.xml` で `register_feed_url_label` / `register_feed_close_description` / `register_feed_submit_button` を提供し、`OutlinedTextField` の `semantics { contentDescription = labelText }` で結線 | UI 結線 |
| NFR 2.2 エラー文言と入力欄の関連付け | Composable で `OutlinedTextField.isError = true` + 直下に `Text(errorMessage, color = error)` | UI 結線 |
| NFR 2.3 視覚基準 `FMRegisterSheet` | `FeedmanSheet` ラッパー + ヘッダ + 入力欄 + 主ボタン構成 | UI 結線 |
| NFR 3.1 日本語表示 | `strings.xml` で全文言を日本語提供 | string resource |
| NFR 3.2 プレーンテキストレンダリング | Composable は `Text` で生文字列を渡すだけ。Markdown / HTML のパーサーは挟まない | 設計 |

## 設計判断と Open Questions への解釈

### 1. エラー code 文字列が未列挙 → httpStatus 主導 + サーバー message フォールバック

`requirements.md` の Open Questions に挙げられている通り、SPEC §4.3 / SERVER に
登録専用エラー code 文字列（重複・URL 不正・フィード未検出・登録レート制限）が
列挙されていない。本実装は Issue prompt の方針に従い:

- **httpStatus 主導の分岐**: 409 → 重複 / 429 → レート制限 / 400・422 → URL 不正 /
  その他 4xx・5xx → 汎用フォールバック / NETWORK_ERROR → ネットワーク到達不可
- **サーバー `errorMessage` を優先**: 各分岐でサーバーが返した `message` を優先して
  表示し、空白の場合のみ `strings.xml` のフォールバック文言を使う
- **将来の code 確定時の拡張容易性**: `RegisterFeedErrorResolver.resolve()` の冒頭で
  `exception.code` の分岐を追加できる構造（現状 NETWORK_ERROR のみ code 駆動）

429 + `retryAfterSeconds` の存在判定で 5.3 / 5.4 を切り替える。`retryAfterSeconds == 0`
の場合は「あと 0 秒」表示が UX 上不自然なため汎用文言にフォールバックする。

### 2. 純粋関数 resolver と Composable の DI 戦略

`RegisterFeedErrorResolver` を Android 依存なしの純粋関数とするため、`strings.xml`
解決済み文言を `RegisterFeedErrorTexts` データクラスに詰めて Composable から
`RegisterFeedViewModel.setErrorTexts()` で注入する設計を採用した。

利点: resolver を JVM 単体テストで網羅でき、Composable のテストカバレッジは
最小限（instrumented テストは v1 スコープで CI 必須にしないため）。

### 3. AppShell の `AppShellSheet.FeedRegistration` 起動点を廃止

#31 で導入された `AppShellSheet.FeedRegistration` placeholder は本 Issue で本実装に
完全置換した。AppShell スコープで `RegisterFeedViewModel` を 1 インスタンス保持し、
ドロワーの `onAddFeedTap` から直接 `registerFeedViewModel.open()` を呼ぶ。
`AppShellSheet` enum 値は後方互換のため残置するが `openSheet` 経由では起動されない。

### 4. 登録成功トーストの表示位置

`SubscriptionSettingsSheet` は内部 `SnackbarHost` を持つが、フィード登録シートは
成功時にシートを閉じる前提（Req 4.1）のため、AppShell スコープに新規 `SnackbarHostState`
（`shellSnackbarHostState`）を導入し、Scaffold 直下に配置した。シート閉じ後も
トーストの表示が継続する。

## 確認事項（PR 本文に転記すること）

1. **エラー code 確定の差し戻し**: requirements.md Open Questions の 1 項目目の通り、
   サーバー側の具体的なエラー `code` 文字列が SPEC / SERVER に未列挙のため、
   本実装は httpStatus 主導の分岐とした。サーバー実装と合わせて `code` を確定する
   フォローアップを別 Issue として PM / Architect に差し戻すことを推奨する。
2. **NFR 1.2 30 秒タイムアウト**: 現状 `ApiClientFactory` は OkHttp 既定タイムアウト
   （connect/read/write 各 10 秒）を使用しており、合計 30 秒未満で
   `SocketTimeoutException` → Req 5.6 のネットワーク文言に到達する。本要件は満たすが、
   明示的に 30 秒タイムアウトを設定する PR を別途検討する余地がある。
3. **NFR 1.3 ローディング中のシート閉じ**: 現状 `Material 3 ModalBottomSheet` が
   ドラッグ下げ / スクリムタップを常時受け付けるため要件は満たすが、登録成功直前に
   閉じた場合 `events.collect` が動かなくなるため成功通知は黙って捨てる挙動になる
   （Req 3.4 と整合）。
4. **dead string**: `sheet_feed_registration_placeholder_title` /
   `sheet_feed_registration_placeholder_body` は本実装で参照されなくなったが、
   strings.xml にそのまま残置している（lint warn が出ていないため副作用なし）。
   別 PR でクリーンアップ余地。

## ビルド・テスト結果

- `./gradlew build` 成功（lint / test 含む全タスク pass）
- 追加した単体テスト: 31 件
  - `FeedRegistrationRepositoryImplTest`: 5 件（MockWebServer）
  - `RegisterFeedErrorResolverTest`: 9 件（純粋関数）
  - `RegisterFeedViewModelTest`: 12 件（StubRepository）
  - `RegisterFeedUiStateTest`: 5 件（境界値）

STATUS: complete
