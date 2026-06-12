# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T06:38:08Z -->

## Reviewed Scope

- Branch: claude/issue-17-impl-apiclient-base-with-retrofit
- HEAD commit: 09fea3a27bd9357c8a7cf3e36e505e78c5d7e002
- Compared to: origin/main..HEAD

## Verified Requirements

- 1.1 — `FeedmanApi` が SPEC §4.2 の typed エンドポイントを suspend 関数として宣言（`getCurrentUser` / `logout` / `deleteCurrentUser` / `updateCrossFeedLastSeen` / `getCrossFeed` / `getSubscriptions` / `deleteSubscription` / `updateSubscriptionSettings` / `resumeSubscription` / `fetchSubscription` / `registerFeed` / `getFeedItems` / `getStarredItems` / `getFeed` / `patchFeed` / `deleteFeed` / `getItem` / `updateItemState` / `searchItems`）。OAuth 開始 / コールバック（`/auth/google/*`）はブラウザリダイレクトであり typed API 対象外（SPEC §3.2）として明示的に除外。テスト `Req 1-1` / `Req 1-3 ...`（4 件）/ `Req 1-5 ...`（2 件）で実 HTTP 経路を検証
- 1.2 — `getCrossFeed` / `getFeedItems` / `getStarredItems` / `searchItems` が `@Query("cursor")` / `@Query("limit")` を nullable で受け取る。テスト `Req 1-2 cross-feed accepts cursor and limit query parameters`
- 1.3 — レスポンス型を `core/model` の `@Serializable` データクラスに紐付け（`CrossFeedPage` / `Page<ItemSummary>` / `Page<StarredItemSummary>` / `Page<ItemSearchHit>` / `Subscription` / `User` / `ItemDetail` / `CrossFeedItem`）。テスト `Req 1-3 ...`（4 件）
- 1.4 — `ItemStateUpdateRequest(isRead: Boolean? = null, isStarred: Boolean? = null)` で部分更新 body を nullable 表現。テスト `Req 1-4 update item state accepts nullable is_read and is_starred fields` / `Req 1-4 update item state with both fields non-null sends both`
- 1.5 — `POST /auth/logout` / `DELETE /api/users/me` / `PUT /api/users/me/cross-feed-last-seen` / `POST /api/subscriptions/{id}/fetch` 含む状態変更系を網羅。テスト `Req 1-5 logout and delete user and update last seen all reach correct endpoints` / `Req 1-5 subscription fetch endpoint is POST and reaches correct path`
- 2.1 — `ApiClientFactory.create(baseUrl=...)` が `NetworkModule` 経由で `AppConfig.baseUrl`（= `BuildConfig.BASE_URL`）から注入される
- 2.2 — `Json { ignoreUnknownKeys = true }`。テスト `Req 2-2 decoder ignores unknown JSON fields without throwing`
- 2.3 — kotlinx.serialization の既定挙動で `null` → nullable プロパティへマップ。テスト `Req 2-3 decoder maps explicit null to nullable property`（`Subscription.faviconUrl = null` / `errorMessage = 非 null` の混在）
- 2.4 — `create(baseUrl, additionalInterceptors: List<Interceptor>, authenticator: Authenticator?)` の公開 API。テスト `Req 4-2` / `Req 4-1`（複数 / 注入）
- 2.5 — 引数省略時の既定（`emptyList()` / `null`）で動作。テスト `Req 2-5 with no extra interceptor or authenticator the api still works`
- 2.6 — `object ApiClientFactory` が状態を持たず、同じ引数で再生成しても同じ契約。テスト `Req 2-6 same input produces FeedmanApi with consistent endpoint contract`
- 3.1 — 2xx は decode 済みモデルを返す（全 200 系テスト）
- 3.2 — `FeedmanErrorMappingInterceptor` + `FeedmanApiProxy` で SPEC §4.3 統一エラー本体 → `FeedmanException`。テスト `Req 3-2 non-2xx with standard error body throws FeedmanException with server fields`
- 3.3 — 429 の `details.retry_after_seconds` を保持。テスト `Req 3-3 429 with details retry_after_seconds is preserved on FeedmanException`
- 3.4 — 解析不能 body は `CODE_UNKNOWN_ERROR` フォールバック。テスト `Req 3-4 non-2xx with malformed body falls back to UNKNOWN_ERROR code`
- 3.5 — `IOException` → `CODE_NETWORK_ERROR`。テスト `Req 3-5 IOException during request becomes NETWORK_ERROR FeedmanException`（`SocketPolicy.DISCONNECT_AT_START`）
- 3.6 — `httpStatus` を `FeedmanException` に保持。テスト `Req 3-6 HTTP status code is exposed on FeedmanException for downstream branching`
- 4.1 — `additionalInterceptors: List<Interceptor>`（順序保持）+ `authenticator: Authenticator?` の公開 API。テスト `Req 4-1 multiple interceptors are invoked in registration order`
- 4.2 — 注入された interceptor が全リクエストで呼ばれる。テスト `Req 4-2 additional interceptor is invoked for each request`
- 4.3 — `authenticator` 引数を `OkHttpClient.Builder.authenticator(...)` に配線（コード上で確認、実 401 検証は #22 の責務）
- 4.4 — `FeedmanErrorMappingInterceptor` を builder の先頭に置き、エラー変換層が OkHttp 拡張点と独立して動作。テスト `Req 4-4 error conversion still works even when an authenticator-like interceptor is present`
- NFR 1.1 — 変更範囲は `core/network/`（4 ファイル新規 + `Placeholder.kt` 削除）/ `di/NetworkModule.kt` / `app/build.gradle.kts` / `gradle/libs.versions.toml` / `app/src/test/kotlin/.../FeedmanApiTest.kt` のみ。`feature/*` および他 `core/*` サブパッケージへの変更なし
- NFR 1.2 — 全テストが `MockWebServer` 経由で実 HTTP 経路を検証。Retrofit インターフェースをモックしない
- NFR 1.3 — `app/src/test/resources/fixtures/` 配下の既存 fixture（`user.json` / `cross_feed_page.json` / `subscription_active.json` / `error_*` 他）を再利用
- NFR 2.1 — Bearer / 401 リフレッシュ / Paging / ItemStateStore は本 PR に含まれない（拡張点のみ提供）
- NFR 2.2 — `NetworkModule` が `AppConfig.baseUrl` 経由で取得。`ApiClientFactory` / `FeedmanApi` 内に固定 URL 文字列なし

## Test Execution

`./gradlew testDebugUnitTest --tests "com.feedman.android.core.network.FeedmanApiTest"` を実行し
`BUILD SUCCESSFUL`（全テスト pass）を確認。

## Findings

なし

## Summary

Issue #17 の全 AC（Requirement 1-4 + NFR）について、SPEC §4.2 のエンドポイント契約を typed
Retrofit インターフェースへ正確に転記し、kotlinx.serialization・`FeedmanException` 変換層・
拡張点 API を完備している。テストは MockWebServer で実 HTTP 経路を回しており CLAUDE.md
テスト規約に準拠。変更範囲も `core/network` / `di` / build 配線 / `app/src/test` に閉じている。

RESULT: approve
