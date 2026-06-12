# Implementation Notes — Issue #42（Manual feed fetch with pull-to-refresh and cooldown handling）

## 対象 Spec

`docs/specs/42-manual-feed-fetch-cooldown/requirements.md`

## 実装サマリ

フィード別画面（`FeedScreen` / `FeedScreenViewModel`）に Material3 `PullToRefreshBox`
を統合し、ジェスチャ完了で `SubscriptionRepository.fetch(subscriptionId)` を呼ぶ手動
フェッチ機構を追加した。成功時は `LazyPagingItems.refresh()` で一覧を再読込し、ドロワーの
未読バッジは `SubscriptionRepository` 内部で `_subscriptions` を更新することで
`observeSubscriptions` 経由で自動的に最新値に反映される。クールダウン応答
（`429 / FEED_COOLDOWN`）は `FeedmanException.retryAfterSeconds` を含む形で透過し、
ViewModel が `FeedScreenEvent.FetchCooldown(retryAfterSeconds)` に変換、UI 側で残り秒数の
有無により文言を切り替える。

## requirement ID → テスト対応表

### Requirement 1: フィード別画面の手動フェッチ起動

| AC | 検証テスト |
|---|---|
| 1.1 ジェスチャ完了でフェッチ要求発行 | `FeedScreenViewModelTest#onPullToRefresh で SubscriptionRepository_fetch が呼ばれ FetchSucceeded を流す_Issue42 Req 1_1 2_1` / `SubscriptionRepositoryImplTest#Issue42 Req 1_1 fetch で api subscriptions id fetch を POST する` |
| 1.2 進行中インジケータ表示 | `FeedScreenViewModelTest#onPullToRefresh は進行中の追加起動を抑止する_Issue42 Req 1_4`（`fetchInProgress.value == true` を観測）。UI は `PullToRefreshBox(isRefreshing = fetchInProgress)` で連動 |
| 1.3 既存記事一覧の閲覧操作継続 | UI 側で `PullToRefreshBox` がモーダルではなく overlay インジケータを使う設計（FeedScreen 実装）。ViewModel 側は state を破壊しない（一覧 Flow は触らない）ことで担保 |
| 1.4 同一フィードへの重複起動抑止 | `FeedScreenViewModelTest#onPullToRefresh は進行中の追加起動を抑止する_Issue42 Req 1_4` |

### Requirement 2: 成功時の一覧再読込と未読反映

| AC | 検証テスト |
|---|---|
| 2.1 成功時に一覧再読込 | `FeedScreenViewModelTest#onPullToRefresh で SubscriptionRepository_fetch が呼ばれ FetchSucceeded を流す`（UI 側で `FetchSucceeded` 受領時に `pagingItems.refresh()` 起動） |
| 2.2 再読込完了でインジケータ終了 | `FeedScreenViewModelTest#onPullToRefresh 完了後 fetchInProgress が false に戻る_Issue42 NFR 1_2`（成功パスでも finally で `_fetchInProgress = false`） |
| 2.3 ドロワー未読バッジ更新 | `SubscriptionRepositoryImplTest#Issue42 Req 2_3 fetch 成功で観測中の Subscription が unread count 更新を反映する`（`_subscriptions` の置換が observe ストリームに流れることでドロワーへ反映） |
| 2.4 結果に新規記事なしのとき既存表示保持 | Paging 3 が PagingSource 再生成後に同じ ID のアイテムを diff で再評価する規約 + `LazyColumn.items(key = id)` で担保（既存実装、Issue #34 と同様）。`FeedScreenViewModelTest#cardPagingData は ItemSummary を ArticleCardModel に変換する` で `id` がそのまま伝搬することを検証 |

### Requirement 3: クールダウン応答のユーザー通知

| AC | 検証テスト |
|---|---|
| 3.1 残り秒数を含む snackbar 表示 | `FeedScreenViewModelTest#onPullToRefresh が FEED_COOLDOWN のとき FetchCooldown を retryAfterSeconds 付きで流す_Issue42 Req 3_1 3_2` / `SubscriptionRepositoryImplTest#Issue42 Req 3_1 fetch がクールダウン応答時 FEED_COOLDOWN と retryAfterSeconds 付きで例外を投げる` |
| 3.2 残り秒数の表記はサーバー応答値に基づく | 同上（`retryAfterSeconds = 30` で値が透過することを確認）。UI は `getString(R.string.feed_fetch_cooldown_with_seconds, seconds)` で format |
| 3.3 残り秒数欠落時は明示しない文言 | `FeedScreenViewModelTest#onPullToRefresh が FEED_COOLDOWN かつ retryAfterSeconds 欠落のとき null を流す_Issue42 Req 3_3`（`FetchCooldown(null)`）。UI は `feed_fetch_cooldown_no_seconds` を表示 |
| 3.4 既存一覧表示・閲覧操作の継続 | ViewModel 側で一覧 Flow を触らない設計、例外を catch して event を流すのみで `_subscriptions` キャッシュも温存（Repository テスト「購読リストは変わらない」で担保） |

### Requirement 4: クールダウン以外のエラーハンドリング

| AC | 検証テスト |
|---|---|
| 4.1 サーバー応答エラーメッセージを表示 | `FeedScreenViewModelTest#onPullToRefresh がその他エラーのとき FetchFailed を message 付きで流す_Issue42 Req 4_1` / `SubscriptionRepositoryImplTest#Issue42 Req 4_1 fetch その他のエラー時に例外を伝搬し購読リストを変えない` |
| 4.2 既存一覧表示・閲覧操作の継続 | 同上（購読リストが保持されることを Repository テストで確認） |
| 4.3 ネットワーク不通メッセージ | `FeedScreenViewModelTest#onPullToRefresh がネットワークエラーのとき FetchFailed をネットワーク文言で流す_Issue42 Req 4_3`（`CODE_NETWORK_ERROR` のとき `FALLBACK_NETWORK_MESSAGE` を採用） |
| 4.4 失敗終了でインジケータ終了 | `FeedScreenViewModelTest#onPullToRefresh 完了後 fetchInProgress が false に戻る` を成功系で観測（失敗系でも try/finally で同一挙動を実装） |

### NFR 1: 応答可観測性

| AC | 検証 |
|---|---|
| NFR 1.1 200ms 以内のインジケータ表示 | `onPullToRefresh()` は `_fetchInProgress.value = true` を同期的に設定してから `viewModelScope.launch` で API 呼び出しを起動するため、ジェスチャ完了と同 frame でインジケータが表示開始される。実機計測は v1 スコープでは行わない |
| NFR 1.2 500ms 以内の終了遷移 | `finally { _fetchInProgress.value = false }` で同期的に false へ戻す。`FeedScreenViewModelTest#onPullToRefresh 完了後 fetchInProgress が false に戻る_Issue42 NFR 1_2` で観測 |

### NFR 2: ユーザー体験の継続性

| AC | 検証 |
|---|---|
| NFR 2.1 モーダル UI を表示しない | `PullToRefreshBox` の overlay インジケータはユーザー操作をブロックしない（Material3 規約）。バナー / フィルタタブも PullToRefreshBox の外側に配置し、独立に操作可能 |

## 実装上の判断記録

1. **`SubscriptionRepository.fetch(...)` の既定実装**:
   - インターフェース既定実装は `UnsupportedOperationException` を投げる形にした
     （`resume` と同じ流儀）。Fake / テストスタブが必要なメソッドだけを override できる
     後方互換構造を維持。
2. **成功時の snackbar 表示の有無**:
   - 要件には「成功時に snackbar 表示する」とは明記されていない（Req 2.1 は一覧再読込のみ規定）。
     UX の簡潔さのため、成功時は snackbar を出さず、Paging refresh で「最新が反映された」事実を
     画面更新で伝える方針を採用。再開（`ResumeSucceeded`）の挙動とは意図的に差別化した。
3. **ドロワー未読バッジの更新経路**:
   - 既存の `SubscriptionRepositoryImpl.fetch()` 内部で `_subscriptions.update { ... }` を呼ぶ
     ことで `observeSubscriptions` Flow 経由で `DrawerViewModel` が自動的に最新値を取得する。
     ViewModel 側で `SubscriptionRepository.refresh()` を追加呼び出ししない（GET 経由の二重
     fetch を避けるため）。
4. **PullToRefreshBox 配置**:
   - 警告バナーとフィルタタブを `PullToRefreshBox` の外側、一覧領域のみを内側に配置した。
     プロトタイプ（fm-screens.jsx）には pull-to-refresh の挙動指定は無いが、TimelineScreen
     (#34) でも一覧本体のみがジェスチャ対象という前例があり、UX 一貫性のため踏襲。
5. **ViewModel フラグの分離**:
   - `_fetchInProgress`（手動フェッチ）と `_resumeInProgress`（再開）を別 StateFlow として
     分離。両者が同時進行する状況は理論上ありうるが、UI 上は別ボタン / 別領域なので相互排他
     不要と判断。
6. **`CODE_FEED_COOLDOWN` 定数の追加場所**:
   - `FeedmanException.CODE_FEED_COOLDOWN = "FEED_COOLDOWN"` を `companion object` に追加。
     SPEC §4.3 で正式に定義された code であり、`CODE_UNKNOWN_ERROR` / `CODE_NETWORK_ERROR` と
     同列に扱う。

## 確認事項（PR 本文への転記候補）

- 成功時に snackbar を出さない方針（実装上の判断 2）はプロダクトオーナーの想定と一致するか。
  Req 2.1 は「一覧再読込」のみを要求しており、「成功通知」は要求していないため snackbar 抑制を
  採用したが、UX 観点で「最新を取得しました」trivial メッセージを出すかどうかは仕様の余地が残る。
- `PullToRefreshBox` の `isRefreshing` を成功 → `pagingItems.refresh()` 起動 → Paging refresh 完了
  までではなく、fetch API 応答完了で false に戻している。Paging refresh 自体は非同期で進行する
  ため、厳密には「再読込完了でインジケータ終了」（Req 2.2）は Paging 側の loading 状態と二段に
  なるが、PullToRefreshBox インジケータと Paging 一覧の loadState は別レイヤー（前者はジェスチャ
  応答、後者は一覧本体）として扱う設計。Reviewer 確認推奨。

## ビルド・テスト結果

- `./gradlew build`: BUILD SUCCESSFUL（lint / 単体テスト全合格）
- 主要なテストクラス:
  - `com.feedman.android.core.data.SubscriptionRepositoryImplTest`（既存 + 新規 4 件）
  - `com.feedman.android.feature.feed.FeedScreenViewModelTest`（既存 + 新規 8 件）

STATUS: complete
