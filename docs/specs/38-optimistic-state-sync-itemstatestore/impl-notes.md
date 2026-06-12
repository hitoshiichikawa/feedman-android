# Implementation Notes — Issue #38

## サマリー

`ItemStateStore` を Singleton として新設し、横断タイムラインと記事詳細シートの既読／スター
状態を画面間で楽観的に同期する一方向データフローを確立した。`GRAND-DESIGN.md` §5.4 が定義する
「ページングデータ < オーバーレイ」のマージ規約を実体化し、購読側 UI（タイムライン / 詳細シート）
を overlay 駆動に切り替えた。スター一覧・横断検索の購読接続は別 Issue（#46 / #48）に分離している
（requirements.md NFR 3.2 / Out of Scope）。

## 主な追加・変更

| ファイル | 種別 | 役割 |
|---|---|---|
| `app/src/main/kotlin/com/feedman/android/core/data/ItemStateStore.kt` | new | overlay の単一データ点、楽観的更新 API、ロールバック、失敗イベント発行 |
| `app/src/main/kotlin/com/feedman/android/di/CoroutineScopeModule.kt` | new | `@ApplicationScope CoroutineScope` の Hilt provider（SupervisorJob + Default） |
| `app/src/main/kotlin/com/feedman/android/feature/timeline/TimelineViewModel.kt` | rewrite | overlays と PagingData の combine、toggleStar / markRead 委譲 |
| `app/src/main/kotlin/com/feedman/android/feature/timeline/TimelineScreen.kt` | modify | カードのスタートグル / 外部リンク既読化を VM 経由で結線、`itemStateFailures` の snackbar 化 |
| `app/src/main/kotlin/com/feedman/android/feature/articledetail/ArticleDetailViewModel.kt` | rewrite | 内部 RawState と overlay を combine し UiState を合成、楽観的更新を store に委譲 |
| `app/src/test/kotlin/com/feedman/android/core/data/ItemStateStoreTest.kt` | new | overlay 即時反映 / ロールバック / 失敗イベント / 複数購読者 / overlay 優先合成 / 冪等 |
| `app/src/test/kotlin/com/feedman/android/feature/timeline/TimelineViewModelTest.kt` | update | overlay 合成 / store 委譲のテストを追加（既存テストは新 API で書き直し） |
| `app/src/test/kotlin/com/feedman/android/feature/articledetail/ArticleDetailViewModelTest.kt` | update | overlay 経由の状態合成 / 他画面 store 更新のシート反映を追加 |

## requirement ID → テスト対応表

| Req ID | 主担当テスト |
|---|---|
| 1.1 楽観値を overlay に即時反映 | `ItemStateStoreTest#setRead で overlay の isRead が即時 true になる_Req 1_1` / `ItemStateStoreTest#setStarred で overlay の isStarred が即時トグルされ updateState を呼ぶ_Req 1_1_4_4` |
| 1.2 単一ストリームで全画面に配信 | `ItemStateStoreTest#複数購読者が同じ overlay 更新を観測する_Req 4_1_4_2_NFR 2_2` |
| 1.3 既読・スターを独立に保持 | `ItemStateStoreTest#既読とスターの overlay は同一 item で独立に保持される_Req 1_3` |
| 1.4 overlay 未設定はサーバー値そのまま | `ItemStateStoreTest#overlay 未設定 item は resolve helper でサーバー値をそのまま返す_Req 1_4_3_3` / `TimelineViewModelTest#overlay にない item はサーバー由来値をそのまま表示する_Issue38 Req 3_3` |
| 2.1 楽観反映後にサーバー更新を発行 | `ItemStateStoreTest#setRead/setStarred 内の updateState 呼び出し検証` |
| 2.2 失敗で overlay を旧値に巻き戻す | `ItemStateStoreTest#スター更新失敗で overlay を旧値に戻し failure イベントを流す_Req 2_2_2_3_2_5` / `#既読更新失敗で overlay を旧値に戻し failure イベントを流す_Req 2_2_2_3_2_5` / `ArticleDetailViewModelTest#既読化サーバー反映失敗で isRead を false に戻し MarkReadFailed イベントを流す_Req 3_3` / `スター更新失敗時にロールバックして StarUpdateFailed イベントを流す_Req 4_5` |
| 2.3 失敗を操作画面にエラー通知 | `TimelineViewModelTest#markReadOnExternalOpen の失敗は ItemStateStore_failures で通知される_Issue38 Req 2_3` / `ArticleDetailViewModelTest` の Mark/Star Failed 系 |
| 2.4 成功時は overlay を維持 | `ItemStateStoreTest#成功時は overlay を維持し追加のロールバックを行わない_Req 2_4` |
| 2.5 楽観適用→失敗→旧値復元のシーケンス | `ItemStateStoreTest#スター更新失敗で...` で collectedOverlays の遷移を観測 |
| 3.1 overlay 値をサーバー値より優先 | `ItemStateStoreTest#resolve は overlay 値をサーバー値より優先する_Req 3_1` / `TimelineViewModelTest#cardPagingData は ItemStateStore overlay 値をサーバー値より優先する_Issue38 Req 3_1` |
| 3.2 新しいサーバーページでも overlay 維持 | `ItemStateStoreTest#新しいサーバーページが来ても overlay は維持される_Req 3_2` |
| 3.3 overlay にない item はサーバー値そのまま | 1.4 と同テスト |
| 3.4 一致時に差分を生じさせない | `ItemStateStoreTest#resolve は overlay 値とサーバー値が一致しても差分を生じさせない_Req 3_4` |
| 4.1 他画面が追加 API なしで反映 | `ItemStateStoreTest#複数購読者が同じ overlay 更新を観測する_Req 4_1_4_2_NFR 2_2` |
| 4.2 共通ストリームで両画面が受け取る | 同上 + `ArticleDetailViewModelTest#他画面で store_setStarred されたとき詳細シートの isStarred が更新される_Issue38 Req 4_1_4_2` |
| 4.3 詳細シートは overlay から購読 | `ArticleDetailViewModelTest#他画面で store_setStarred されたとき...`（uiState の combine 検証） |
| 4.4 タイムラインは overlay から購読 | `TimelineViewModelTest#cardPagingData は ItemStateStore overlay 値をサーバー値より優先する_Issue38 Req 3_1` |
| 5.1 シート起動で overlay を既読化 | `ArticleDetailViewModelTest#open は Loading_を経由して Content へ遷移し isRead を true にする_Req 1_1_3_1` |
| 5.2 外部リンク起動で overlay を既読化 | `TimelineViewModelTest#markReadOnExternalOpen で既読が立っていなければ ItemStateStore_markRead を呼ぶ_Req 2_2` / `ArticleDetailViewModelTest#markReadOnOpenExternal は未読 Content から既読化リクエストを発火する_Req 4_3` |
| 5.3 既読 item には API を再送しない（冪等） | `ItemStateStoreTest#markRead は既に既読の item に対して API を再送しない_Req 5_3` / `TimelineViewModelTest#markReadOnExternalOpen は既読時には API を再送しない_Issue38 Req 5_3` / `ArticleDetailViewModelTest#既に既読の記事を open しても updateState を再送しない_Req 3_5` / `markReadOnOpenExternal は未読のときのみ既読化する_Req 4_3` |
| NFR 1.1 100ms 以内反映 | overlay 更新が `MutableStateFlow.update` で同期的に行われ、サーバー呼び出しは別コルーチン（NFR 1.2）。`ItemStateStoreTest#setRead で overlay の isRead が即時 true になる` で `overlays.first()` を await せずに値が取れることが疎証 |
| NFR 1.2 サーバー結果を待たずに配信 | 同上 |
| NFR 2.1 テスト観測可能なシーケンス公開 | `ItemStateStore.overlays` / `ItemStateStore.failures` を `StateFlow` / `SharedFlow` として公開 |
| NFR 2.2 複数購読者への伝播テスト | `ItemStateStoreTest#複数購読者が同じ overlay 更新を観測する_Req 4_1_4_2_NFR 2_2` |
| NFR 3.1 影響範囲を本体と購読接続に限定 | 変更ファイル一覧（上記表）の通り。スター一覧 / 検索は未変更 |
| NFR 3.2 スター一覧・検索を含まない | 同上 |

## 判断記録

### 連続トグルの inflight 競合の扱い

`setStarred(item, true)` の inflight 中にユーザーが `setStarred(item, false)` を呼んだ場合、
本実装は **両方の API を順序通り直列に投げる**（後発の楽観値が overlay を上書き）。
1 件目が失敗してロールバックされる時、その時の baseline は呼び出し時点の値で固定するため、
直前の楽観値を上書きしてさらに新値を立てた状態と整合する。完全な競合制御（直前のリクエスト
を cancel する等）は v1 スコープ外（Out of Scope: オフラインキュー再送相当）として
保守的な挙動に倒している。利用者の体感としては「失敗時に最新トグルが消える」のは違和感が
あり得るが、ネットワーク不安定時に楽観値が混乱するよりは保守的。

### `ItemStateStore` を Singleton + コンストラクタ scope 注入とした理由

- Singleton 化はグランドデザインの規約。複数画面共通の状態同期点であるため。
- `viewModelScope` を使うと当該 ViewModel が消えた瞬間に inflight サーバーリクエストが
  キャンセルされ、optimistic overlay と server state が永久に乖離するリスクがある。
- そこで `@ApplicationScope` 修飾子付きの `CoroutineScope`（`SupervisorJob + Dispatchers.Default`）
  を Hilt で提供し、ViewModel ライフサイクルから独立した寿命を担保。
- テストでは Hilt を通さず、`CoroutineScope(Dispatchers.Unconfined)` を直接渡して
  `runTest` 内で完結させる。

### Timeline VM の `cardPagingDataForTest` で `overlays.take(1)` を使った理由

`cachedIn` 後の `Flow<PagingData<T>>` を `combine(stateFlow)` すると、StateFlow が
完了しないため `asSnapshot()` 利用時に `UncompletedCoroutinesError` で `runTest` が失敗する。
本番経路では Compose の Lifecycle で collector が cancel されるため問題なく、テスト
専用エントリポイントだけ `overlays.take(1)` で 1 つ目のスナップショットで完了させる。
合成結果の検証には 1 回の値で十分（`combine` の挙動として overlay が更新されれば再 emit
されることは Issue #38 Req 4.x の他のテストで検証済み）。

### `ArticleDetailEvent` の後方互換維持

`ItemStateStore.failures` は既読・スターを区別する `ItemStateFailure.Kind` を持つが、
シート UI 側 `ArticleDetailEvent` も `MarkReadFailed` / `StarUpdateFailed` を維持している
（snackbar 文言が違うため UI 側で `when` 分岐したいニーズを優先）。`ArticleDetailViewModel`
の init ブロックで store.failures → ArticleDetailEvent への変換を行い、当該シートが
表示中の item の failure のみイベントを発火している。タイムライン側 (`TimelineScreen`) は
直接 `itemStateFailures` を購読する（同 item に紐づかない別画面の失敗も snackbar 表示）。

### `markReadOnExternalOpen` のシグネチャ拡張

`currentIsRead: Boolean` を引数に追加した。理由は store 側で「現在の既読値」を知らない
（overlay 合成は呼び出し側の責務）ため、冪等判定を呼び出し側で行ってもらう設計。
タイムラインのカードは overlay 合成済みの `ArticleCardModel.isRead` を渡し、詳細シートは
`uiState.value.isRead` を渡す。

## 確認事項（レビュワー判断ポイント）

- **store.failures は singleton scope の SharedFlow なので、replay=0 でも複数購読者が
  collect しなければ失敗イベントが「誰にも届かない」可能性がある**。現状の UI 構造では
  TimelineScreen と ArticleDetailSheet が並行して collect しているため通常は問題ないが、
  シート未表示・タイムライン未表示の状態（例えばスター一覧画面 #46 表示中）でトグルが発火
  すると、その画面が #46 で store.failures を購読していないと snackbar が出ない。
  別 Issue（#46 / #48）で購読接続を追加する際は同様に `failures` を購読する責務がある
  ことに留意する必要がある（本 Issue のスコープ外）。
- **連続トグルの直列投入**: 「inflight 中に再トグル」発生時の挙動は上記「判断記録」の通り
  保守的だが、長期的にはリクエスト直列化キュー（Mutex per itemId 等）の導入を検討する余地が
  ある。v1 ではユーザー操作頻度が低い前提で対応せず。
- **ArticleDetailViewModel の uiState 初期 emit**: `stateIn(SharingStarted.Eagerly, initialValue=Hidden)`
  でシート未起動時の初期値を Hidden に固定。`combine` の最初の emit も `Hidden + emptyMap()`
  なので二重 emit にはなっていない。
- **CoroutineScopeModule の `Dispatchers.Default`**: store 内部の updateState 呼び出しは
  Retrofit が独自スレッドで I/O を吸収するため、scope 自体は CPU バウンド既定で十分。
  Main で動かす必要はない（StateFlow の collect 側で Lifecycle を考慮するのは購読側の責務）。

## STATUS

STATUS: complete
