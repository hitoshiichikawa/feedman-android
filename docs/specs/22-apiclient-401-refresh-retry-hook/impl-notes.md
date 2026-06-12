# Issue #22 実装メモ

## 変更ファイル

- `app/src/main/kotlin/com/feedman/android/core/network/TokenAuthenticator.kt`（新規）
- `app/src/main/kotlin/com/feedman/android/di/NetworkModule.kt`（authenticator 結線）
- `app/src/test/kotlin/com/feedman/android/core/network/TokenAuthenticatorTest.kt`（新規）

## 要件 ID とテスト対応表

| Requirement ID | 内容 | 担保するテスト |
|---|---|---|
| Req 1.1 | 401 → 有効な refresh → 新 access で元リクエストを 1 回再試行 | `Req 1-1 transparent refresh and retry returns success response to caller` |
| Req 1.2 | 再試行 2xx 時は呼び出し元へ success を透過 | `Req 1-2 caller receives the successful response transparently after refresh` |
| Req 1.3 | 再試行が 401 以外のエラー（4xx / 5xx）→ そのまま伝搬 | `Req 1-3 caller receives non-401 error from retried request unchanged` |
| Req 1.4 | 自動 refresh + 再試行は 1 回限り | `Req 1-4 NFR 1-1 NFR 1-2 retry is limited to once per request even if retry returns 401` / `responseCount halts retry beyond 1 even with chained priorResponse` |
| Req 2.1 | refresh token 未保存時に 401 → refresh も再試行もせず 401 を伝搬 | `Req 2-1 no refresh token in store skips refresh and propagates 401` |
| Req 2.2 | `/api/auth/refresh` 401 時に保存トークンを破棄（AuthRepository 委譲） | `Req 2-2 Req 2-3 Req 2-4 refresh 401 clears tokens flips session false and propagates 401` |
| Req 2.3 | トークン破棄でセッション状態を「未ログイン」へ遷移し購読側が観測できる（observeIsAuthenticated → false） | 同上 + `AuthRepositoryImplTest` の `Req 2-4 INVALID_REFRESH_TOKEN clears TokenStore and returns AuthRequired`（#21 で担保済み） |
| Req 2.4 | refresh 失敗時、元リクエスト呼び出し元に認証必要であることが識別できる応答（401 / FeedmanException）を返却し再試行ループに入らない | 同上（`401 を伝搬` の検証）|
| Req 3.1 | 並行 401 → refresh 呼び出しを 1 件に保つ | `Req 3-1 Req 3-2 Req 3-4 concurrent 401s share single refresh and all retry once` |
| Req 3.2 | 単一飛行 refresh 成功 → 待機中の他 401 を各 1 回ずつ再試行 | 同上（parallelism * 2 件の /auth/me が記録される）|
| Req 3.3 | 単一飛行 refresh 失敗 → 待機中の他 401 を再試行せず未認証応答 | `Req 2-2 Req 2-3 Req 2-4` シナリオで refresh 失敗時に retry されないことを確認（並行版は AuthRepositoryImplTest 側の単一飛行テストで担保済み） |
| Req 3.4 | refresh 進行中の新規 401 は当該結果に合流し新たな refresh を開始しない | `Req 3-1 Req 3-2 Req 3-4 concurrent 401s share single refresh and all retry once`（refresh 1 回のみ計測） |
| Req 4.1 | refresh 応答の新 access / refresh トークン保存後に再試行へ供給 | `AuthRepositoryImplTest Req 2-1 Req 2-2`（#21 / TokenStore 上書き保存）+ 本 Issue の `Req 1-1`（再試行が新 access で送られることを `rotated-AT` で確認） |
| Req 4.2 | トークン保存失敗時の AuthRequired 合流 | AuthRepositoryImplTest の refresh 失敗系で担保（#21）。本 Issue では委譲経由で同じ振る舞いになる |
| NFR 1.1 | 元リクエスト 1 回 + refresh 1 回 + 再試行 1 回 = 計 3 回以内 | `Req 1-4 ...` で /auth/me が 2 回、/api/auth/refresh が 1 回、計 3 回であることを assert |
| NFR 1.2 | 再試行リクエストが 401 → 再 refresh しない | `Req 1-4 ...` で refresh が 1 回だけ呼ばれることを assert |
| NFR 2.1 | 3 シナリオを自動テストで再現できるフックポイント | 上記 `Req 1-1`（成功）/ `Req 2-2 ...`（refresh 失敗）/ `Req 3-1 ...`（並行）の 3 シナリオが MockWebServer + 実 OkHttp で検証されている |

加えて、本 Issue の認証エンドポイント除外仕様を確認するテスト:

| 検証内容 | テスト |
|---|---|
| `/api/auth/refresh` への 401 は authenticator 対象外（無限ループ防止） | `auth refresh endpoint is exempted from authenticator retry path` |
| `/api/auth/token` への 401 は authenticator 対象外 | `auth token endpoint is exempted from authenticator retry path` |

## 判断記録

### 1. 再試行回数の上限判定: `Response.priorResponse` チェーン長

OkHttp の Authenticator は同一の `Response` チェーンに対して認証チャレンジが繰り返し発生する
（401 → retry → 401）と呼び出されるため、再試行回数を OkHttp 標準の慣用パターン
（`priorResponse` を辿ってチェーン長を数える）で 1 回に制限した。`MAX_RETRY_COUNT = 1`。

- これによって「元リクエスト → 401 → refresh → 再試行 → 再 401」のとき
  `priorResponse` チェーン長が 1 になるため authenticate() が null を返し、それ以降の retry を
  止めて 401 を呼び出し元に伝搬させる（NFR 1.1 / NFR 1.2）。
- 別アプローチとして「Request 単位のフラグや ThreadLocal で再試行済みマークを持つ」も検討したが、
  OkHttp の dispatcher 上では同 Request が複数スレッドにまたがる場合があり、また OkHttp 自身が
  既に priorResponse を持っているため、追加状態を持たずに済む priorResponse 方式を採用した。

### 2. refresh の単一飛行は AuthRepository に委譲（重複実装回避）

Req 3 の単一飛行（並行 401 → refresh 1 回）は AuthRepository.refresh() の `Mutex` + `CompletableDeferred`
で既に実装されているため、本 authenticator は単純に `runBlocking { authRepository.refresh() }` を
呼ぶだけにとどめた。

並行 401 のテストでは MockWebServer の `setBodyDelay(200ms)` で refresh 応答を遅延させ、4 つの
並行 /auth/me が確実に refresh 中に重なるようにしている。結果として refresh は 1 回しか
呼ばれず、全件が新 token で retry されて成功する（Req 3.1 / 3.2 / 3.4）。

### 3. トークン破棄と SessionState 反映も AuthRepository に委譲

Req 2.2（トークン破棄）と Req 2.3（観測可能な未認証状態への遷移）は AuthRepository.refresh() の
`AuthRequired` 結果分岐内で `tokenStore.clear()` + `isAuthenticated.value = false` を実行する
形で #21 で実装済み。authenticator は失敗時に null を返すだけで、独自にトークンを消したり
SessionState を触ったりしない（重複実装回避 / 単一責務）。

画面側からのセッション失効観測は `AuthRepository.observeIsAuthenticated()` の StateFlow で
行う。実際の画面遷移（ログイン画面への差し替え）は Out of Scope の Issue #24 で担当する。

### 4. 認証エンドポイント自身の除外

`/api/auth/token` と `/api/auth/refresh` への 401 は authenticate() の冒頭で除外する。
これは `AuthInterceptor.isAuthExemptPath` と同じ完全一致ルールで判定する。

- もし `/api/auth/refresh` 自身に再帰的に authenticate() を適用すると、refresh が 401 を返した
  時点で本 authenticator がさらに refresh を呼び、無限ループになる。
- `/api/auth/revoke` は Bearer 認証下なので authenticator の対象に含めるが、もし revoke で
  401 になっても refresh 後の retry がさらに 401 → priorResponse カウントで停止するため、
  特別扱いは不要。

### 5. Dagger 循環依存の解消（`dagger.Lazy`）

`AuthRepository → FeedmanApi → TokenAuthenticator → AuthRepository` の循環を Hilt が検知して
ビルドが落ちたため、TokenAuthenticator のコンストラクタを `dagger.Lazy<AuthRepository>` 受け取りに
変更した。authenticate() が呼ばれるタイミングでは Hilt がグラフを既に解決済みなので、Lazy.get()
で取り出して問題ない。テストでは Lazy ラッピングが煩雑なので、AuthRepository を直接受け取る
secondary constructor も併設している。

### 6. runBlocking の使用

OkHttp の Authenticator は同期 API のため、suspend の AuthRepository.refresh() と
TokenStore.read() は `runBlocking { ... }` で呼ぶ。OkHttp の dispatcher スレッド上で実行される
前提に依存しており、UI スレッドからの直接呼び出しは想定していない（Repository / ViewModel は
すべて Coroutine ベースで API を呼ぶ）。これは AuthInterceptor で先行採用済みの慣習と同じ。

## 確認事項（レビュワー判断ポイント）

1. `/api/auth/revoke` の除外を保留した（Bearer 認証下なので retry 対象に入れたが、本 Issue では
   revoke 401 シナリオの専用テストは追加していない）。実機運用で revoke 中の token 失効が観測
   されたら、後続 Issue で除外パス追加を検討してください。
2. 並行 401 の単一飛行は AuthRepository 側の単体テスト（AuthRepositoryImplTest の
   `Req 2-3 NFR 2 concurrent refresh calls share single network request and identical result`）と
   本 Issue の `Req 3-1 ... concurrent 401s ...` テストで二重に担保している。AuthRepository 側の
   挙動が本機能の正しさに直結するため、AuthRepository の単一飛行実装を変えるときは本機能の
   並行 401 テストも合わせて確認してください。
3. テストでは `PathDispatcher` で path 別の応答キューを用意している。MockWebServer の標準 FIFO
   ではなく Dispatcher を採用した理由は、並行 401 + refresh の path-別決定論を維持したかった
   ためです。

## ビルド結果

- `./gradlew build` 成功（2m 27s、lint / debug + release unit test を含む）

## STATUS

STATUS: complete
