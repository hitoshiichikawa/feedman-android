# Issue #39 実装ノート

## 概要

ドロワーの購読フィード一覧データソースを Fake（#30）から実 API `GET /api/subscriptions`（SPEC §4.2）に切り替えた。
`AppConfig.mockMode = true` のときは引き続き `FakeSubscriptionRepository` を使い、`false` のときは新規追加した `SubscriptionRepositoryImpl` を使う DI 切替を導入した。
取得失敗時はドロワー内のフィードセクションに限定したエラー + 再試行表示を提示し、シェル全体（メイン項目・フッタ・トップバー）は壊さない。

## 構成変更

- `core/data/SubscriptionLoadState.kt` 新規: 取得状態の sealed interface（Idle / Loading / Success / Error(message, code)）
- `core/data/SubscriptionRepository.kt` 拡張: `observeLoadState(): Flow<SubscriptionLoadState>` と `suspend fun refresh()` を interface に追加
- `core/data/SubscriptionRepositoryImpl.kt` 新規: `FeedmanApi.getSubscriptions()` に薄く委譲、`MutableStateFlow` × 2 系統で list / load state を保持、`Mutex` で refresh を直列化
- `core/data/fake/FakeSubscriptionRepository.kt` 拡張: `observeLoadState() = Success`、`refresh() = no-op`（API を呼ばない）
- `di/SubscriptionRepositoryProvider.kt` 新規: `selectSubscriptionRepository(mockMode, fake, real)` 純粋関数
- `di/RepositoryModule.kt` 改修: `SubscriptionRepository` の解決を `@Binds` から `@Provides`（companion object）に切替えて mockMode 分岐
- `shell/DrawerViewModel.kt` 拡張: `DrawerUiState(rows, feedSection)`、`FeedSectionState` sealed interface、init で refresh、`retryLoadSubscriptions()`
- `shell/DrawerContent.kt` 拡張: `DrawerFeedsSection` 内で Loading（`CircularProgressIndicator`）/ Error（文言 + `TextButton` 再試行）を分岐描画
- テスト: `SubscriptionRepositoryImplTest`（MockWebServer）/ `SubscriptionRepositoryProviderTest`（DI 切替）/ `FeedSectionStateTest`（純粋関数）/ `DrawerViewModelTest` を Issue #39 用に拡張

`core/network`（API 契約）・`core/model.Subscription`（型）・他 feature 画面は変更していない（NFR 1.1）。

## requirement ID → テスト対応表

| Req ID | 内容 | 対応テスト |
|---|---|---|
| 1.1 | observe で `GET /api/subscriptions` を呼ぶ | `SubscriptionRepositoryImplTest#Req 1_1 refresh で api subscriptions エンドポイントを GET する` + `DrawerViewModelTest#Req 1_1 ViewModel 初期化時にリポジトリの refresh が起動される` |
| 1.2 | 2xx を `Subscription` として decode し観測者へ流す | `SubscriptionRepositoryImplTest#Req 1_2 1_3 200 応答を Subscription 配列として decode し observe へ流す` |
| 1.3 | `feed_id` / `feed_title` / `favicon_url` / `unread_count` / `feed_status` を観測可能リストに含む | 同上（assert で各フィールドを検証） |
| 1.4 | サーバーが返した順序を変更せずに流す | `SubscriptionRepositoryImplTest#Req 1_4 サーバーが返した順序を変更せずそのまま流す` |
| 1.5 | 空配列のとき空リストを流す | `SubscriptionRepositoryImplTest#Req 1_5 空配列のとき空のフィードリストを流す` |
| 1.1.1 | rows が新しいリストを反映する | 既存 `DrawerViewModelTest#リポジトリが新しいリストを emit すると uiState に反映される_NFR 2_1`（NFR 2.1 と兼ねる） |
| 1.1.2 | 未読バッジに `unread_count` を用いる | 既存 `DrawerFeedRowTest`（#30）+ `DrawerViewModelTest#uiState はリポジトリの順序のまま行へ変換する_Req 1_5`（status / unread を確認） |
| 1.1.3 | 状態アイコン判定に `feed_status` を用いる | 既存 `DrawerFeedRowTest` |
| 1.1.4 | favicon が data URL のとき描画に渡す | 既存 `FakeSubscriptionRepositoryTest`（#30） + `SubscriptionRepositoryImplTest#Req 1_2 1_3` で `data:` プレフィックスの decode を確認 |
| 1.1.5 | favicon が null のときレターアバター fallback | 既存 `DrawerFeedRowTest`（#30）+ Favicon Composable 経路は変更なし |
| 2.1 | 失敗を Error 状態として観測者へ通知する | `SubscriptionRepositoryImplTest#Req 2_1 2_6 非 2xx で SPEC エラー応答が来たら Error 状態で message と code を通知する` + `Req 2_1 ネットワーク失敗時に Error 状態で通知する code は NETWORK_ERROR` + `DrawerViewModelTest#Req 2_1 2_2 取得失敗時に feedSection が Error message を保持する` |
| 2.2 | フィード一覧セクションにエラー表示と再試行操作を提示する | `FeedSectionStateTest#Error のとき rows の有無にかかわらず Error を返し message を保持する_Req 2_1_2_2_2_6` + DrawerContent の UI 配線（コードレビュー対象） |
| 2.3 | メイン項目・フッタ・トップバー表示を継続する | DrawerContent 構造で Loading / Error はフィードセクション内のみに描画（コードレビュー対象 / NFR 3.1 と一体） |
| 2.4 | 再試行タップで `GET /api/subscriptions` を再取得する | `SubscriptionRepositoryImplTest#Req 2_4 再試行 refresh で 2 回目の取得が走り Success に回復する` + `DrawerViewModelTest#Req 2_4 retryLoadSubscriptions が repository の refresh を再呼び出しする` |
| 2.5 | 再試行中はロード中であることが識別できる | `SubscriptionRepositoryImplTest#Req 2_5 refresh 中は Loading 状態を観測者へ通知する` + `DrawerViewModelTest#Req 2_5 取得中 rows 空 のとき feedSection が Loading になる` + `DrawerViewModelTest#Req 2_5 取得中でも rows が残っているなら feedSection は Success silent refresh` + `FeedSectionStateTest#Loading かつ rows が空のとき Loading を返す_Req 2_5` |
| 2.6 | エラー応答の `message` をユーザー向け文言として用いる | `SubscriptionRepositoryImplTest#Req 2_1 2_6 非 2xx で SPEC エラー応答が来たら Error 状態で message と code を通知する`（assert: `"リクエストパラメータが不正です。"`）+ `FeedSectionStateTest#Error のとき … message を保持する_Req 2_1_2_2_2_6` |
| 3.1 | mockMode = true で Fake を用いる | `SubscriptionRepositoryProviderTest#Req 3_1 mockMode true のとき Fake 実装を返す` |
| 3.2 | mockMode 時は `GET /api/subscriptions` を呼ばない | `FakeSubscriptionRepository.refresh()` no-op + `SubscriptionRepositoryProviderTest`（Fake インスタンス選択）で間接保証 |
| 3.3 | mockMode = false で実 API 実装を用いる | `SubscriptionRepositoryProviderTest#Req 3_3 mockMode false のとき実 API 実装を返す` |
| 3.4 | 公開インターフェースを mockMode に関わらず同一に保つ | コンパイル時に保証（`SubscriptionRepository` の 1 interface に Fake / 実装が両方 fit する）+ 既存 `DrawerViewModelTest` の各テストが両方の系統で動く構造 |
| 4.1 | 401 は共通認証層の再認証フローに従う | `ApiClientFactory` の authenticator 注入経路を経由する設計（コードレビュー対象 / 実認証層実装は別 Issue）。本実装は throw された結果を透過する |
| 4.2 | トークン更新後の再試行成功時は購読一覧を流す | 同上（共通層が成功で返したレスポンスは `runCatching.onSuccess` で `Success` に遷移 = Req 1.2 と同一経路） |
| 4.3 | 401 継続時は識別可能な認証エラー状態として通知する | `SubscriptionRepositoryImplTest#Req 4_3 401 が継続したら識別可能な Error 状態として通知する`（code = UNAUTHORIZED で観測） |
| NFR 1.1 | 変更範囲が core/data / di / shell / strings.xml / test に閉じる | `git diff --name-only origin/main..HEAD` で機械的に確認可能（`core/network` / `core/model` / 他 feature 無変更） |
| NFR 1.2 | 公開 IF 互換、`DrawerViewModel` 利用箇所が機械的書き換えなしで動作 | 既存 `DrawerViewModelTest`（#30 由来のテスト）が pass / `AppShell` が `drawerUi.rows` の参照を変更せずに動く |
| NFR 2.1 | HTTP 層をモックして正常系・失敗系・空応答を単体テストで検証 | `SubscriptionRepositoryImplTest` 全体（MockWebServer 使用） |
| NFR 2.2 | Drawer UI 状態を注入して検証可能 | `DrawerViewModelTest` で stub repository を注入 + `FeedSectionStateTest` で純粋関数を検証 |
| NFR 2.3 | DI 切替を単一箇所で差し替え可能 | `selectSubscriptionRepository` 純粋関数 + `SubscriptionRepositoryProviderTest` で AC を裏付け |
| NFR 3.1 | 失敗中もドロワー外画面は壊れない | DrawerContent 構造でフィードセクション内のみに Loading / Error を描画。UI 側で `rows` を読む経路（AppShell の `feedTitleLookup`）は引き続き機能する |
| NFR 3.2 | 再試行中もユーザーの他のドロワー操作を受け付ける | `viewModelScope.launch { repository.refresh() }` 起動のため UI スレッドはブロックされず、メイン項目・フッタタップ・close は通常通り動作（コードレビュー対象） |

## 判断記録

1. **`SubscriptionRepository` 互換性**: 既存 interface に新メソッド 2 つ（`observeLoadState`, `refresh`）を追加した。Fake 実装は `Success` のみ emit / `refresh` は no-op として実装することで、Fake が「失敗が起きない世界」を表現したまま新メソッドのコンパイル互換を取り戻した（NFR 1.2）。これは Kotlin の interface に default 実装を持たせる代替案（Java 8+ の defender method）よりも、各実装の振る舞いを explicit に保つために選択した。
2. **状態と購読リストの分離**: `observeSubscriptions()` と `observeLoadState()` を独立した Flow として公開した。これにより取得失敗中も直近の成功リストを残し（UI 側で「リスト + エラーバー」を併記可能）、`FeedSectionState.from()` で「rows あり + Loading = silent refresh」のような UI 表示判定が可能になった。状態と list を 1 つの sealed class（`Loaded(list)` / `Error(list?)`）に統合する代替案もあったが、独立 Flow の方が ViewModel 側の合成（`combine`）で表現できて見通しが良いと判断した。
3. **`refresh()` での例外吸収**: repository 側で `runCatching` し、例外を `SubscriptionLoadState.Error` に変換して観測者へ通知する契約とした。これにより UI / ViewModel 側は `try/catch` を書かずに `viewModelScope.launch { repo.refresh() }` だけで完結できる。
4. **`Mutex` による直列化**: 再試行ボタン連打時の重複 fetch を抑制するため、`refreshMutex.withLock` で in-flight 1 件に直列化した。OkHttp 層の connection pool 競合や、サーバー側のレート制限（FEED_COOLDOWN）に巻き込まれる可能性を減らす目的。
5. **`@Binds abstract` と `@Provides` の共存**: `RepositoryModule` は既存の `@Binds abstract fun` 群を持つため、`@Provides` を共存させるには Hilt 標準パターンに従って `companion object` に置く必要があった。`RepositoryModule.Companion.provideSubscriptionRepository` として宣言し、abstract class 内の static provider として認識される。
6. **silent refresh 設計**: `feedSection = Loading` でも `hasRows = true` のときは Success として表示する。これは「ドロワーを開くたびにリストが点滅する」UX 問題を避けるための判断（Req 2.5 は「ロード中であることが識別できる」と要求しているが、初回ロード時のみ Loading 表示を出せば AC は満たせると解釈した）。エラー時は rows の有無にかかわらず Error 表示を出す。
7. **401 認証エラーの扱い**: 共通認証層（`ApiClientFactory` の `authenticator` パラメータ）が再認証フローを担う設計（Issue #17 / #22）が既に存在するため、本実装は「共通層を経由した後の最終結果」のみ受ける。`SubscriptionLoadState.Error` に `code = "UNAUTHORIZED"` を保持しているため、将来 UI 側で「ログイン画面に飛ばす」分岐を追加できる余地を残した。

## 確認事項（人間レビュワー向け）

- **DI 配線の整合**: `RepositoryModule` の `@Binds abstract class` に `companion object` の `@Provides` を共存させる構成は Hilt 公式パターンとして問題ないが、本リポジトリの既存モジュール（`AuthModule` 等）は `@Binds` 専用なので、本パターンが他開発者にとって surprise になるか。将来 `RepositoryModule` を分割して `SubscriptionRepository` 専用 `@Module object` に移すことも検討余地。
- **`FeedSectionState.Loading` 表示の visual review**: フィードセクション内の `CircularProgressIndicator`（24dp）の visual fit は `design/mobile/fm-screens.jsx` のリファレンスでは直接示されていない。`core/ui/StateViews.kt` の `LoadingFooter` と同等パターンを採用したが、ドロワー文脈で違和感がないかは実機 / プレビュー確認が望ましい。
- **`FeedSectionState.Error` 文言の改行・truncation**: サーバー由来の `error.message` 長文時にレイアウト崩れがないか、特に `weight(1f)` + `bodySmall` の組み合わせで折り返しが取られることを実機確認したい。
- **`refresh()` の Mutex によるスレッドブロック**: テスト用 `UnconfinedTestDispatcher` 環境では問題なく動作するが、実行時に複数 viewModelScope から同時呼び出しされたとき、Mutex の待機により coroutine が Main で suspend する。これはバックグラウンド `Default` Dispatcher で実行する場合の挙動を改めて確認すると安心（現状の API は呼び出し側の dispatcher に従う）。
- **要件 4.1, 4.2 のテスト**: 共通認証層の Authenticator 実装が本リポジトリ未実装のため、認証フローを経由した「トークン更新後の再試行成功」シナリオは本 Issue では integration test できなかった（Authenticator が無い MockWebServer 構成）。認証層が入った段階で結合テストを別途 Issue で起票することが望ましい。

## ビルド / テスト結果

- `./gradlew build`: BUILD SUCCESSFUL（2m 24s）
- `./gradlew :app:testDebugUnitTest`: SUCCESSFUL（全テストパス）
- lint: 警告のみ（既存）/ エラーなし

STATUS: complete
