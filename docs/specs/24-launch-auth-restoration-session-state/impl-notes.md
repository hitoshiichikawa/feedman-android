# Implementation Notes — Issue #24 起動時認証復元 / 3 状態 SessionState

## 概要

Issue #21〜#23 で導入された 2 状態（LoggedOut / LoggedIn）の `SessionState` を
`Restoring` / `LoggedIn` / `LoggedOut` の 3 状態に拡張し、アプリ起動時に保存済みトークンを
使った認証復元シーケンスと、復元中のスプラッシュ表示を実装した。

## 主要な実装判断

### 復元戦略（Req 1.1〜1.5 / NFR 1.1〜1.2 / NFR 3.1）

`AuthRepositorySessionStateProvider` の構築直後 (`init { scope.launch { restoreAndFollow() } }`) に
以下の Phase 1 → Phase 2 のシーケンスを実行する:

- **Phase 1 (起動時復元)**:
  1. 初期値 `SessionState.Restoring` で `MutableStateFlow` を作成（Req 1.1）。
  2. `TokenStore.read()` で access token の有無を確認。
     - access token 無し → ネットワーク I/O せずに `LoggedOut` 確定（Req 1.3 / NFR 1.2）。
     - access token あり → `AuthRepository.refresh()` を 1 回試行（SERVER.md の refresh
       ローテーション前提と整合）。
  3. refresh 結果でフォールバック判定:
     - `Success` → `LoggedIn` 確定（Req 1.2）。
     - `AuthRequired` → `AuthRepositoryImpl` 側で TokenStore 消去 + `isAuthenticated=false`
       遷移が起きているため、`LoggedOut` 確定（Req 1.4 / NFR 3.1）。
     - `NetworkFailure` / `ServerError` → 保存トークンを保持したまま `LoggedIn` フォールバック
       （Req 1.5。最初の API 401 で #22 が回復を試みる前提）。
  4. **5 秒タイムアウト**: 全 Phase 1 を `withTimeoutOrNull(5_000L)` で囲み、超過時は
     `fallbackOnTimeoutOrFailure` でアクセストークン有無により LoggedIn / LoggedOut に倒す
     （NFR 1.1）。
- **Phase 2 (以降の追従)**:
  - `authRepository.observeIsAuthenticated().drop(1).collect { … }` で
    **初期値を捨て** て以降の遷移のみ反映する。
  - 初期値を捨てる理由: `StateFlow` は購読開始時に現在値を即時 emit するが、Req 1.5 の
    ネットワーク失敗フォールバック時には Repository 側 `isAuthenticated` が `false` のまま
    なので、初期値を採用すると Phase 1 のフォールバック判定（LoggedIn）を覆して LoggedOut
    に倒してしまう。`drop(1)` で「exchange 成功で true / 401 起因 refresh 失敗で false」の
    後続遷移のみを反映する（Req 4.3 / Req 5.3）。

### MockMode の互換維持（NFR 1.1 / 既存挙動）

`MockModeSessionStateProvider` は `Restoring` を経由せず、`AppConfig.mockMode` から直接
`LoggedIn` / `LoggedOut` に確定する既存挙動を維持した（requirements.md Out of Scope の
「mockMode 連動の暫定信号」と整合）。`MockModeSessionStateProviderTest` は変更不要。

### スプラッシュ表示（Req 2.1〜2.3）

`AppShell` の `when (sessionState)` 分岐に `Restoring` を追加し、新規
`RestoringSplash` Composable で `Box + Column(CircularProgressIndicator + Text)` の
中央寄せレイアウトを描画する。LoginScreen / LoggedInShell のいずれも描画しない
（Req 2.2 / 2.3）。文言は strings.xml に `session_restoring_description` を追加。

## 要件 → テスト対応表

| Req ID  | テスト関数 |
|---|---|
| 1.1     | `AuthRepositorySessionStateProviderTest#Req 1_1 initial state is Restoring before restore coroutine resumes` |
| 1.2     | `AuthRepositorySessionStateProviderTest#Req 1_2 stored token and refresh success transitions to LoggedIn` |
| 1.3     | `AuthRepositorySessionStateProviderTest#Req 1_3 no stored token transitions Restoring to LoggedOut without network call` |
| 1.4     | `AuthRepositorySessionStateProviderTest#Req 1_4 INVALID_REFRESH_TOKEN clears tokens and transitions to LoggedOut` |
| 1.5     | `AuthRepositorySessionStateProviderTest#Req 1_5 network failure during refresh keeps token and falls back to LoggedIn` |
| 2.1〜2.3 | `AppShell.kt#RestoringSplash` の Composable 構造（`Restoring` 分岐で LoginScreen / LoggedInShell を描画しないことをコードで保証）。UI 単体テストは Compose UI Test 対応を別 Issue で扱う方針 |
| 3.1〜3.3 | `AppShell.kt#AppShell` の `when` 分岐 + `AuthRepositorySessionStateProviderTest#Req 1_2` で LoggedIn 復元経路を担保 |
| 4.1〜4.2 | `AppShell.kt#AppShell` の `when` 分岐（LoggedOut → LoginScreen） |
| 4.3     | `AuthRepositorySessionStateProviderTest#Req 4_3 after restore LoggedIn transitions to LoggedOut when isAuthenticated flips to false` |
| 4.4     | AppShell シェル下のシート Composable は LoggedInShell スコープに閉じており、LoggedOut へ遷移すると AppShell の `when` が `LoginScreen` に切替わり Composition から外れる構造で担保。挙動テストは Compose UI Test 必要のため別 Issue（既存方針） |
| 5.1     | `SessionState.kt` の sealed class 構造で Restoring / LoggedIn / LoggedOut 3 状態を区別 |
| 5.2     | `AuthRepositorySessionStateProvider` を `@Singleton` で Hilt にバインドすることで単一インスタンス公開（`AuthModule.provideSessionStateProvider`） |
| 5.3     | `AuthRepositorySessionStateProviderTest#Req 5_3 after restore LoggedOut transitions to LoggedIn when login succeeds` |
| NFR 1.1 | `AuthRepositorySessionStateProviderTest#NFR 1_1 refresh timeout falls back to LoggedIn when token is stored` および `… falls back to LoggedOut when token is missing` |
| NFR 1.2 | `AuthRepositorySessionStateProviderTest#Req 1_3` で `refreshCallCount == 0` を assert |
| NFR 2.1 | `AuthModule.provideSessionStateProvider` の `@Singleton` バインドで担保 |
| NFR 2.2 | `MockModeSessionStateProviderTest#state は StateFlow として現在値を即時に公開する` および `AuthRepositorySessionStateProvider.state` の `StateFlow` 公開 |
| NFR 3.1 | `AuthRepositorySessionStateProviderTest#Req 1_4` で `tokenStore.read() == null` を assert（AuthRepositoryImpl 側の clear 契約を fake で再現） |
| NFR 3.2 | `AuthRepositoryImplTest`（Issue #21 で既存）と #22 系で担保。本 Issue では Provider の追従挙動を `Req 4_3` でカバー |

## 確認事項（レビュワー向け）

1. **NFR 1.1 のタイムアウト判定経路**: 保存トークン有り + refresh が長時間ブロックした場合、
   タイムアウト後 `fallbackOnTimeoutOrFailure` で LoggedIn にフォールバックする実装としたが、
   実環境では OkHttp 側のタイムアウトの方が短い（接続/読取で 10〜30 秒前）想定なら、
   5 秒の追加タイムアウトは「Phase 1 全体が 5 秒で必ず確定する」保証用の安全網と位置付けた。
   実際のチューニングが必要であれば `restoreTimeoutMillis` を DI 経由で注入可能にする選択肢が
   ある（現状はテスト用に `overrideRestoreTimeoutForTest` のみ公開）。
2. **drop(1) 設計の代替案**: 「Phase 1 結果と `observeIsAuthenticated()` を同期させてから
   collect する」案も検討したが、NetworkFailure フォールバック時には Repository 側で
   `isAuthenticated=true` に上書きできない（TokenStore は保存トークンあり、しかし refresh は
   未成功）。`drop(1)` で初期値を捨てることが requirements.md Req 1.5 を素直に満たす最小の
   実装と判断した。
3. **Req 4.4（シート破棄）の UI 単体テスト**: AppShell の `when` 分岐で `LoggedInShell` が
   Composition から外れることでシート Composable も自動破棄されるため、コード構造で担保
   されている。Compose UI Test での挙動再現は既存方針（本リポジトリでは UI 単体テストを
   instrumented test 必須としない）に従い別 Issue でのカバーを推奨。

## 派生タスク候補

- Compose UI Test での `Restoring` 中のスプラッシュ表示と LoggedOut 遷移時のシート破棄の
  挙動テスト（Req 2.1〜2.3 / Req 4.4 をエンドツーエンドで再現）
- `restoreTimeoutMillis` の DI 経由注入と、運用環境での値の見直し

STATUS: complete
