# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-35-impl-item-detail-state-repository
- HEAD commit: ab9a5b9
- Compared to: origin/main..HEAD
- Feature Flag Protocol: opt-out（細目チェックは適用しない）

## Verified Requirements

- 1.1 — `ItemDetailRepositoryImplTest.Req 1-1`（GET `/api/items/{id}` を MockWebServer の `recorded.method` / `encodedPath` で検証）/ `ItemDetailRepositoryImpl.getItem` で `FeedmanApi.getItem` に委譲
- 1.2 — `Req 1-2`（200 + `item_detail.json` を decode し、`content` / `author` / ItemSummary 相当の全フィールドを assertEquals で網羅検証）
- 1.3 — `Req 1-3`（`is_date_estimated:true` を持つインラインボディで `detail.isDateEstimated` が true として伝搬されることを assertTrue）
- 1.4 — `Req 1-4`（`hatebu_fetched_at:null` のインラインボディで `detail.hatebuFetchedAt` が null として保持されることを assertNull）。`explicitNulls=false` 適用後も既存 nullable プロパティは全て `= null` 既定値を持つため decode 挙動不変であることを確認（`ApiItem.kt` / `Subscription.kt` / `Page.kt` / `FeedmanApi.kt` の `String?` / `Boolean?` 全箇所が `= null` 既定）
- 2.1 — `Req 2-1`（PUT `/api/items/{id}/state` を method / encodedPath で検証）
- 2.2 — `Req 2-2`（body JSON を `parseToJsonElement` で実パースし、`obj.containsKey("is_starred")` を assertFalse で強アサーション）+ `FeedmanApiTest.Req 1-4` に同方向の `assertFalse(body1.contains("is_starred"))` を追加
- 2.3 — `Req 2-3`（同様に `is_read` 欠落を assertFalse）+ `FeedmanApiTest.Req 1-4` 強化
- 2.4 — `Req 2-4`（両キーの値が `false` / `true` で送信されることを assertEquals）
- 2.5 — `Req 2-5`（両 null で `FeedmanException(code=CODE_UNKNOWN_ERROR)` が throw され、かつ `server.requestCount == 0` で HTTP 不送信を機械的に保証）。`ItemDetailRepositoryImpl.updateState` 冒頭の guard 節と整合
- 2.6 — `Req 2-6`（200 応答時に例外が起きず `server.requestCount == 1` を確認）
- 3.1 — `Req 3-1`（404 + `error.code/message` ボディで `FeedmanException.code=="NOT_FOUND"` / `errorMessage` / `httpStatus=404` を assertEquals）
- 3.2 — `Req 3-2`（500 で同様に `INTERNAL` / `httpStatus=500` を検証）
- 3.3 — `Req 3-3 network failure during getItem` と `Req 3-3 network failure during updateState` の 2 件で `SocketPolicy.DISCONNECT_AT_START` を用い、`CODE_NETWORK_ERROR` を確認
- 3.4 — Req 2-5 の `requestCount == 0` で「バリデーション失敗時に副作用が残らない」ことを担保。GET/PUT 失敗時のローカル副作用は v1 スコープでローカルキャッシュ非保持（SPEC §1.3）のため自明（impl-notes.md にも記載）
- NFR 1.1 — 全公開 API（`getItem` / `updateState`）が MockWebServer 経由の JVM 単体テストで検証されている
- NFR 1.2 — Requirement 1 / 2 / 3 の各分岐に最低 1 件のテストを配置
- NFR 2.1 — パス・メソッド・body 構造・レスポンス型を Req 1-1 / 1-2 / 2-1 / 2-2 / 2-3 / 2-4 で実 HTTP として検証
- NFR 2.2 — エラーレスポンスを `FeedmanException` に変換して透過（Req 3.1 / 3.2 で検証）。必須フィールド欠落 fixture は本 Issue で新規追加せず、Issue #15 / #17 の既存責務に委任（impl-notes.md 確認事項として明示）

## Findings

なし

## Summary

requirements.md の全 numeric ID（1.1〜1.4 / 2.1〜2.6 / 3.1〜3.4 / NFR 1.1〜2.2）が実装またはテストで観測可能にカバーされている。MockWebServer による PUT ボディ検証は `parseToJsonElement` での実 JSON パース + `containsKey` チェックという正確な手段を採用しており、`String.contains("is_starred")` 的な弱いアサーションではない。`Json.explicitNulls=false` のグローバル適用は、リポジトリ内の全 `@Serializable` クラスの nullable フィールドが `= null` 既定値を持つため decode 挙動を破壊せず、`./gradlew testDebugUnitTest` の UP-TO-DATE / BUILD SUCCESSFUL で回帰を確認した。boundary は `core/data` 新規追加 / `core/network` の最小修正（`explicitNulls=false` 切替と KDoc 更新のみ）/ `di/RepositoryModule.kt` への 1 行 binding 追加 / fixture は既存流用 / テストは `app/src/test/` 配下に閉じており、#36 UI・#38 楽観更新ロジックには一切踏み込んでいない。

RESULT: approve
