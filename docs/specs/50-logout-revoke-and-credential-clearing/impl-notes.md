# Implementation Notes — Issue #50「Logout revoke and credential clearing」

## 設計判断（Open Question への回答）

### Q1. リセット対象キャッシュの棚卸し

requirements.md の Open Question で挙げられている「ユーザースコープでリセット対象とすべき
キャッシュ」の具体構成要素を、`core/data` 配下の実装を棚卸しした結果、以下に限定した:

| キャッシュ | 種別 | reset の挙動 |
|---|---|---|
| `ItemStateStore` | 既読・スター overlay の `MutableStateFlow<Map<String, ItemStateOverlay>>` | overlay を空マップに置換 |
| `SubscriptionRepositoryImpl` | 購読リスト `MutableStateFlow<List<Subscription>>` + 取得状態 `MutableStateFlow<SubscriptionLoadState>` | リストを空、状態を Idle に置換 |
| `CrossFeedRepositoryImpl` | セッション固定の `sessionSinceTime: String?` | `null` に戻す |

`UserRepository` は自身に in-memory cache を持たない設計（Issue #49 で確定）。`AccountSheetViewModel`
の `cachedUser` フィールドは ViewModel スコープに閉じており、`logout()` 内で明示的に `null`
代入で破棄する。

**統一機構の選択**: 「multibinding で `UserScopedCache` interface を集約する」案と「明示列挙の
`LogoutCoordinator` コンストラクタ引数で個別注入する」案のうち、後者を採用した。理由:

- 新たなキャッシュが追加されても **コンストラクタ引数の追加が必要**になり、リセット漏れに
  気付きやすい
- multibinding はテスト時の差し替えが複雑になる
- 現状のキャッシュ数（3 件）は明示列挙で十分に管理可能。`UserScopedCache` interface 自体は
  契約として残し、将来 multibinding に移行する余地は保持

### Q2. ログアウト直後の補助フィードバック

「ログアウトしました」等のスナックバー併出は Out of Scope に従い **実装しない**。ログイン画面
への遷移自体が十分なフィードバックとなる前提で運用する。

## 実装サマリ

### コンポーネント分割

1. **`UserScopedCache` interface** (`core/data/UserScopedCache.kt`): ユーザースコープのリセット契約
2. **`LogoutCoordinator` interface** + **`LogoutCoordinatorImpl`** (`core/auth/LogoutCoordinator.kt`):
   ログアウト全工程の調停。`withTimeoutOrNull(10_000L)` で revoke 上限、`runCatching` で各
   cache.reset を独立に保護
3. **既存 store に `reset()` を追加**:
   - `ItemStateStore.reset()`: overlay map を空に
   - `SubscriptionRepositoryImpl.reset()`: list + loadState を初期状態に
   - `CrossFeedRepositoryImpl.reset()`: sessionSinceTime を null に
4. **`AccountSheetViewModel.logout()`**: 多重押下防止 + fetchJob キャンセル + cachedUser 破棄 +
   進行中状態 + 完了時 Hidden 遷移
5. **`AccountSheet.AccountSheetLogoutSection`**: ログアウトボタン + 進行中インジケータ
6. **`strings.xml`**: 文言追加（`account_sheet_logout_button` / `account_sheet_logout_in_progress`）

### SessionState 遷移の経路

`LogoutCoordinator.perform()` 内で `AuthRepository.revoke()` を呼ぶと、`AuthRepositoryImpl` が
`tokenStore.clear()` と `isAuthenticated.value = false` を実行する。この変化を
`AuthRepositorySessionStateProvider` が `observeIsAuthenticated().drop(1).collect` で観測し、
`SessionState.LoggedOut` を `_state` に流す。AppShell の `when(sessionState)` がこれを受けて
`LoginScreen` を描画する。これにより:

- Req 4.1: SessionState が LoggedOut へ遷移
- Req 4.2: AppShell がログイン画面を描画
- Req 4.3: AccountSheet 自体は ViewModel 内で Hidden に切り替えるため、シートが閉じる

## requirement ID → テスト対応表

| Req ID | 要件 | テスト |
|---|---|---|
| 1.1 | ログアウト UI 要素を表示 | `AccountSheetLogoutSection` 実装（UI 表示の常時化） |
| 1.2 | logout 押下で処理を 1 回開始 | `AccountSheetViewModelTest`: `Issue50 Req 1_2 logout で LogoutCoordinator perform が 1 回呼ばれる` |
| 1.2（Hidden 時の no-op） | Hidden での logout 抑制 | `AccountSheetViewModelTest`: `Issue50 Req 1_2 Hidden 状態での logout は no-op` |
| 1.3 | 進行中の再押下不可 | `AccountSheetViewModelTest`: `Issue50 Req 1_3 logout 中の再 logout 呼び出しは無視される` |
| 1.4 | 進行中の視覚表現 | `AccountSheetViewModelTest`: `Issue50 Req 1_3 1_4 logout 中は logoutInProgress true_完了で Hidden` |
| 2.1 | revoke を 1 回呼ぶ | `LogoutCoordinatorTest`: `Req 2_1 perform は revoke を 1 回呼び TokenStore を消去する` |
| 2.2 | サーバーエラーでも TokenStore 消去 | `LogoutCoordinatorTest`: `Req 2_2 revoke のサーバーエラーでも ...` |
| 2.3 | ネットワーク失敗でも中断しない | `LogoutCoordinatorTest`: `Req 2_3 revoke のネットワーク失敗でも ...` |
| 2.4 | 完了後に TokenStore が空 | `LogoutCoordinatorTest`: 上記 Req 2_1 / 2_2 / 2_3 内で `assertNull(tokenStore.read())` |
| 3.1 | ユーザースコープキャッシュリセット | `LogoutCoordinatorTest`: `Req 3_1 perform は ItemStateStore overlay を空にする` |
| 3.1（個別） | ItemStateStore reset | `ItemStateStoreTest`: `reset で overlay が空になる_Issue 50 Req 3_1` 他 |
| 3.1（個別） | SubscriptionRepositoryImpl reset | `SubscriptionRepositoryImplTest`: `Issue50 Req 3_1 reset で購読リストと load state が ...` 他 |
| 3.1（個別） | CrossFeedRepositoryImpl reset | `CrossFeedRepositoryImplTest`: `Issue50 Req 3_1 reset で sessionSinceTime が null に戻る` 他 |
| 3.2 | 新セッションで前ユーザー状態を再現しない | （観測可能挙動 = 上記 3.1 群のリセットで満たされる。新セッション側 UI は ViewModel が再生成され空状態から始まる） |
| 3.3 | cachedUser を破棄 | `AccountSheetViewModelTest`: `Issue50 Req 3_3 logout 後の再 open では cachedUser が再現せず再フェッチが走る` |
| 4.1 | SessionState を LoggedOut へ遷移 | `LogoutCoordinatorTest`: `Req 4_1 perform 完了後に observeIsAuthenticated が false に遷移する`（SessionStateProvider はこの変化を観測する経路 / Issue #24 既存テストで担保） |
| 4.2 | ログイン画面を描画 | （`AppShell` の when(sessionState) 既存実装 + Req 4.1 のテストで間接担保） |
| 4.3 | アカウントシートを閉じる | `AccountSheetViewModelTest`: `Issue50 Req 1_3 1_4 logout 中は logoutInProgress true_完了で Hidden`（完了時 Hidden になることを検証） |
| 4.4 | 他ボトムシートも破棄 | （SessionState 遷移経由で `LoggedInShell` 全体が再生成されるため `articleDetailViewModel` / `registerFeedViewModel` 等は破棄される。観測可能挙動として Req 4.2 と同等） |
| 5.1 | 全失敗パスでログアウト完遂 | `LogoutCoordinatorTest`: `Req 5_1 perform は例外を投げない_キャッシュ reset が独立に呼ばれる` + 上記 Req 2_2 / 2_3 |
| 5.2 | エラーメッセージを表示しない | （UI 実装で AccountSheet は logout 用 error state を持たない。観測可能挙動として `AccountSheetUiState.Visible` の `loadState` に Error は積まれない設計） |
| NFR 1.1 | 1 秒以内に進行中表現 | `AccountSheetViewModel.logout()` 同期実装で `_uiState.value = current.copy(logoutInProgress = true)` を最初に実行（即時反映） |
| NFR 1.2 | 10 秒以内に完遂 | `LogoutCoordinatorTest`: `NFR 1_2 REVOKE_TIMEOUT_MILLIS は 10 秒に設定されている`（境界値宣言テスト） |
| NFR 2.1 | 例外発生時も TokenStore 残さない | `LogoutCoordinatorImpl.perform()` の `runCatching` で握り潰し + `AuthRepository.revoke()` の `tokenStore.clear()` 保証 |
| NFR 2.2 | ログにトークンを含めない | `LogoutCoordinator` / `LogoutCoordinatorImpl` 双方ともログ出力なし。`AuthRepositoryImpl.revoke` も内部値をログ出力しない |
| NFR 3.1 | ログに email 含めない | 同上。`AccountSheetViewModel.logout()` も email を扱わない（cachedUser を `null` 代入で破棄するのみ） |

## 確認事項（レビュワー判断ポイント）

1. **ログアウトボタンの配置位置**: プロトタイプ `design/mobile/fm-sheets.jsx` の `FMAccountSheet`
   ではフッタに置く想定。本実装ではユーザー情報の真下（区切り線直下）にミニマルに配置した。
   後続デザイン調整 Issue で位置を再検討する可能性あり
2. **`UserScopedCache` の使い方**: interface 自体は contract として残しているが、
   `LogoutCoordinatorImpl` は **個別 Singleton 実装型**でコンストラクタ引数を受ける明示列挙
   方式を採用。multibinding を採用しなかった理由は本ドキュメント上部の設計判断記述を参照
3. **`CrossFeedRepositoryImpl.reset()` の実効性**: Pager 再生成時に `newPagingSource()` が
   自動で `sessionSinceTime = null` を実行するため、ログアウト → 新ログイン直後の Pager 再生成
   ではリセットは冗長になる。それでも明示的に reset を呼ぶのは「DI Singleton として
   `CrossFeedRepositoryImpl` が長寿命のため前ユーザー値が一瞬残らないこと」を保証するため
4. **NFR 1.2 の物理的検証**: 10 秒タイムアウトの実機検証は本実装では境界値宣言テスト
   （`REVOKE_TIMEOUT_MILLIS == 10_000L`）で代替している。実 OkHttp の NO_RESPONSE 動作を
   待つテストは実時間 10 秒を消費するため CI には組まない判断
5. **失敗イベントバッファ**: `ItemStateStore.reset()` は `_failures: MutableSharedFlow` を
   明示的にクリアしない（SharedFlow には clear API がなく、replay=0 構成のため購読者破棄で
   自動的に消える）。ログアウト後に旧 ViewModel が破棄される経路で実害は無いが、設計上の
   留意点として記録

## STATUS

STATUS: complete
