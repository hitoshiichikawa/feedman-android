# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-13T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-23-impl-custom-tabs-login
- HEAD commit: 285e6f5
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `LoginViewModelTest.Req 1_1 initial uiState is Idle` + `LoginScreen.kt`（`login_title` / `login_body` / `login_google_button` の `stringResource` 参照）。タイトル・案内文・Google ボタンを LoginScreen で描画
- 1.2 — `AppShell.kt` の `when (sessionState)` 分岐で `LoggedOut → LoginScreen` / `LoggedIn → LoggedInShell` の二択。既存 `AppShellViewModelTest`（修正で `LoginPlaceholderScreen` → `LoginScreen` 文言のみ更新）でカバー
- 1.3 — `LoginScreen.kt` 全域で `MaterialTheme.colorScheme` / `MaterialTheme.typography` を参照し、テーマトークン経由で配色が解決される（FeedmanTheme 自体のライト/ダーク追従は #25 で検証済み）
- 2.1 — `LoginViewModelTest.Req 2_1 to 2_3 startGoogleLogin emits authorization URL...` + `AuthorizationUrlBuilderTest.Req 2_1 build appends path to baseUrl...` / `...returns full URL...`
- 2.2 — `AuthorizationUrlBuilderTest.Req 2_2 build includes code_challenge and S256 method` / `Req 2_2 build percent-encodes code_challenge value` + `LoginViewModel.startGoogleLogin` が `PkceGenerator.generate()` 由来の challenge を渡し、`AuthorizationUrlBuilder.build` がクエリに含める
- 2.3 — `AuthorizationUrlBuilderTest.Req 2_3 build includes flow=native query parameter`
- 2.4 — `LoginViewModelTest.Req 2_4 startGoogleLogin stores generated code_verifier in SavedStateHandle`（SavedStateHandle 経由でプロセス再生成耐性を確保）
- 2.5 — `LoginViewModelTest.Req 2_5 startGoogleLogin while LaunchingCustomTabs is no-op`（`fakePkce.callCount == 1` で 2 回目押下が抑止されることを確認）
- 3.1 — `AuthCallbackDispatcherTest.Req 3_1 dispatch emits URI to intents flow` / `Req 3_1 cold start dispatch is replayed to late collector` + `LoginViewModelTest.Req 3_1 AuthCallbackDispatcher emit triggers exchange via onDeepLink` + `MainActivity.onCreate`/`onNewIntent` が `authCallbackDispatcher.dispatch` を呼ぶ実装、`AndroidManifest.xml` の intent-filter（scheme/host/path 完全固定）
- 3.2 — `LoginViewModelTest.Req 3_2 onDeepLink success calls AuthRepository_exchange with stored verifier`（auth_code と保持中の code_verifier ペアで exchange 呼び出しを確認）
- 3.3 — `AuthRepositorySessionStateProviderTest.Req 3_3 transitions to LoggedIn when AuthRepository emits true` + `LoginViewModelTest.Req 3_3 onDeepLink success transitions uiState to Idle`（AppShell が SessionStateProvider 経由で LoggedIn に切替わる経路を担保）
- 3.4 — `LoginViewModelTest.Req 3_4 NFR 1_3 onDeepLink success clears stored verifier`
- 3.5 — `LoginViewModelTest.Req 3_5 startGoogleLogin while Exchanging is no-op`（Exchanging 状態での再押下が PKCE 生成を増やさない）
- 4.1 — `LoginViewModelTest.Req 4_1 onDeepLink INVALID_GRANT transitions to Error_Server`（`LoginError.Server.isInvalidGrant()` 経由で分類を確認）
- 4.2 — `LoginViewModelTest.Req 4_2 onDeepLink network failure transitions to Error_Network`
- 4.3 — `LoginViewModelTest.Req 4_3 startGoogleLogin after Error generates fresh code_verifier`（Error 状態からの再押下で新 PKCE が SavedStateHandle に上書き保存される）
- 4.4 — `LoginViewModelTest.Req 4_4 NFR 1_3 onDeepLink failure clears stored verifier`（失敗時にも `clearStoredVerifier` で破棄）
- 5.1 — `LoginViewModelTest.Req 5_3 NFR 2_2 onDeepLink without auth_code does not call exchange`（`uiState !is Error` を assert し、Custom Tab を閉じた場合や auth_code 欠落でエラー表示されないことを担保）
- 5.2 — `LoginViewModelTest.Req 4_3 startGoogleLogin after Error generates fresh code_verifier` を流用（Custom Tab 閉鎖後の再押下は Idle / LaunchingCustomTabs どちらからでも新 PKCE 生成パスに到達する）
- 5.3 — `LoginViewModelTest.Req 5_3 NFR 2_2 onDeepLink without auth_code does not call exchange` + `Req 5_3 onDeepLink without saved verifier does not call exchange`
- NFR 1.1 — `LoginViewModel` / `AuthRepositorySessionStateProvider` / `AuthCallbackDispatcher` のいずれにも `Log.*` / クラッシュレポート / 解析イベントへの `codeVerifier` 出力なし（静的確認）
- NFR 1.2 — `SavedStateHandle` 経由のプロセス常駐保存のみで、`EncryptedPrefsTokenStore` 等の永続ストアに書き込まない（`Req 2_4` テスト + 実装の静的確認）
- NFR 1.3 — `Req 3_4 NFR 1_3` / `Req 4_4 NFR 1_3` で成功・失敗いずれでも破棄を確認
- NFR 2.1 — `LoginViewModelTest.NFR 2_1 onDeepLink with non-feedman scheme` / `NFR 2_1 onDeepLink with wrong host or path` + `AndroidManifest.xml` の intent-filter（scheme/host/path 全固定）
- NFR 2.2 — `Req 5_3 NFR 2_2 onDeepLink without auth_code does not call exchange`
- NFR 3.1 — `LoginViewModel.onDeepLink` 内の suspend `exchange` は `viewModelScope.launch` で起動されるため、プロセス生存中は待機継続（静的確認、impl-notes.md 84 行で明記）
- NFR 3.2 — 既存 `AuthRepositoryImpl` (#21) の Retrofit/OkHttp 既定 timeout に従う（impl-notes.md 84 行 / 既存 `AuthRepositoryImplTest` でカバー）

## Findings

なし

## Summary

要件 1〜5 / NFR 1〜3 の全 ID について、JVM 単体テスト（`LoginViewModelTest` / `AuthorizationUrlBuilderTest` / `AuthCallbackDispatcherTest` / `AuthRepositorySessionStateProviderTest`）または静的実装で観測可能なカバーが確認できた。`./gradlew :app:testDebugUnitTest` 該当クラス群はすべて green。差分は feature/login / core/auth / di / MainActivity / AndroidManifest / strings.xml / AppShell（最小限の `LoginPlaceholderScreen` → `LoginScreen` 差し替えのみ）/ app/src/test に閉じており、tasks.md の `_Boundary:_` 制約および「#24 起動時復元・#50 ログアウトに踏み込まない」境界を遵守。`AppShell.kt` の変更は `LoggedOut` 分岐先を本実装に切替えただけで、`SessionState` の起動時復元ロジックには触れていない。Custom Tabs 起動・intent-filter の実機検証は instrumented 領分のため JVM テスト不在を理由とした reject はしない。

RESULT: approve
