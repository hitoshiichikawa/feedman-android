# Implementation Notes — Issue #1 Android アプリスケルトン

## 採用バージョン

| 項目 | バージョン | 備考 |
|---|---|---|
| Gradle | 8.10.2 | wrapper でコミット済み（NFR 3.1） |
| AGP | 8.7.3 | compileSdk 35 と整合する安定版（GA） |
| Kotlin | 2.0.21 | + `kotlin-compose` 2.0.21（K2 compose compiler） |
| KSP | 2.0.21-1.0.28 | Hilt は kapt ではなく KSP を採用（高速化） |
| Hilt | 2.52 | KSP 経由でコンパイル |
| Compose BOM | 2024.10.01 | material3 等は BOM 経由で版管理 |
| AndroidX core-ktx | 1.15.0 | |
| activity-compose | 1.9.3 | |
| lifecycle-runtime/viewmodel-compose | 2.8.7 | `collectAsStateWithLifecycle` 用 |
| navigation-compose | 2.8.4 | |
| hilt-navigation-compose | 1.2.0 | |
| Coroutines | 1.9.0 | core / android / test |
| Turbine | 1.1.0 | Flow テスト |
| JUnit | 4.13.2 | JVM 単体テスト |
| compileSdk / targetSdk | 35 | ローカル環境で利用可能な最新（android-36 はローカル未配置） |
| minSdk | 26 | SPEC §2 / CLAUDE.md 技術スタックと整合 |

> 設計書（design.md）の Technology Stack には「Compose BOM 最新安定版」「compileSdk 35
> を上限とする（ローカル環境制約）」とあり、本実装は同制約に従って GA 安定版の組み合わせ
> を採用した。

## Requirement → テスト / 成果物 対応表

| Req ID | 担保箇所 |
|---|---|
| 1.1 | `./gradlew build` 成功（BUILD SUCCESSFUL in 50s、119 tasks） |
| 1.2 | `app/build.gradle.kts` の `applicationId="com.feedman.android"` / `namespace="com.feedman.android"` / `minSdk=26` |
| 1.3 | `app/build.gradle.kts` の `kotlin { jvmToolchain(17) }` + Compose BOM + `material3` 依存 |
| 1.4 | `settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts`（すべて Kotlin DSL） + `gradle/libs.versions.toml` |
| 1.5 | `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.{jar,properties}` をコミット |
| 1.6 | `app/src/test/.../FakeItemRepositoryTest.kt`（3 ケース）/ `TimelineViewModelTest.kt`（2 ケース）/ `AppConfigTest.kt`（2 ケース） |
| 1.7 | `gradle/libs.versions.toml` 冒頭コメントで方針を明記。すべて GA 安定版 |
| 2.1 | `app/src/main/kotlin/com/feedman/android/` を `sourceSets` で有効化 |
| 2.2 | `core/{model,network,auth,data,designsystem,ui}` の 6 サブパッケージを配置 |
| 2.3 | `feature/{login,timeline,feed,articledetail,starred,search,registerfeed,subscriptionsettings,account}` の 9 サブパッケージを配置 |
| 2.4 | `shell/{AppShell,DrawerContent,Navigation}.kt` + `di/{RepositoryModule,AppConfigModule}.kt` |
| 2.5 | `FeedmanApplication`（@HiltAndroidApp）+ `MainActivity`（@AndroidEntryPoint、setContent）。AndroidManifest に宣言 |
| 2.6 | `feature/timeline` は `core/data.ItemRepository` のみ参照。`core/*` から `feature/*` への import なし（grep で確認） |
| 2.7 | 具体実装を持たないパッケージに `Placeholder.kt`（KDoc 付き package-level file）を配置。`feature/login` / `feature/timeline` も同様に package marker を持つ |
| 3.1 | `di/RepositoryModule` + `di/AppConfigModule` を `@InstallIn(SingletonComponent::class)` で集約 |
| 3.2 | `core/data/ItemRepository`（interface）+ `core/data/fake/FakeItemRepository`。`FakeItemRepositoryTest` の 3 テストで担保 |
| 3.3 | `RepositoryModule` の `@Binds @Singleton` が `FakeItemRepository` をデフォルトバインド |
| 3.4 | `FakeItemRepository` は `@Singleton class @Inject` で Hilt 管理。手動 `object` シングルトンなし |
| 4.1 | `AppShell` が `AppConfig.mockMode == false` 時に `LoginPlaceholderScreen()` を表示（Compose ロジック） |
| 4.2 | `LoginPlaceholderScreen` は静的 Composable のみ、`AuthRepository` を inject しない。disabled `Button` を配置 |
| 4.3 | `AppShell` が `mockMode == true` 時に `ModalNavigationDrawer + Scaffold(topBar) + Navigation()` を表示 |
| 4.4 | `TimelineViewModel` が `ItemRepository`（= Fake）から `StateFlow<TimelineUiState>` を生成し、`TimelineScreen` が描画。`TimelineViewModelTest` で検証 |
| 4.5 | `AppConfigModule` が `BuildConfig.MOCK_MODE` から `AppConfig.mockMode` を生成。既定 false → `AppShell` で `LoginPlaceholderScreen()` |
| 5.1 | `app/build.gradle.kts` の `buildConfigField("String", "BASE_URL", "\"$defaultBaseUrl\"")`、`defaultBaseUrl = findProperty("feedman.baseUrl") ?: ...` |
| 5.2 | 同上の `buildConfigField("boolean", "MOCK_MODE", defaultMockMode.toString())` |
| 5.3 | `defaultBaseUrl` の既定値 `"https://dev.feedman.example.com"`。コメントで根拠を明示 |
| 5.4 | `defaultMockMode = ... ?.toBoolean() ?: false`。`AppConfigTest` の equality テストで挙動を保証 |
| 5.5 | Version Catalog / source / `gradle.properties` に実 API キー・トークンを記載していない（grep 確認）。README に方針記述 |
| 6.1 | `README.md` 「ビルド手順」節（JDK 17 / `./gradlew build` / `feedman.mockMode=true` の例） |
| 6.2 | 同節の Gradle プロパティ表と `-P` フラグ / `gradle.properties` 記法の例 |
| NFR 1.1 | `gradle/libs.versions.toml` で主要依存をすべて GA 安定版で宣言 |
| NFR 1.2 | 安定版不在の依存は本 Issue には存在しないため例外宣言は不要（コメント方針のみ記述） |
| NFR 2.1 | `app/src/test/` を `sourceSets` の `test.kotlin.srcDir` で有効化、3 ファイル / 計 7 テストが実行される |
| NFR 2.2 | `app/src/androidTest/.gitkeep` をコミットしてディレクトリ確保（テストファイルは置かず CI 必須化もしない） |
| NFR 3.1 | `gradle/wrapper/gradle-wrapper.properties` の `distributionUrl` を `gradle-8.10.2-bin.zip` に固定 |

## 実装上の判断

### `BuildConfig.BASE_URL` の既定値

requirements 5.3 は「開発サーバー向けの既定 URL を設定する」と要求するが、本 Issue 時点で
開発サーバー URL は未確定（後続 Issue 範囲）。そのため非機密かつ判別容易な
`https://dev.feedman.example.com` をプレースホルダ既定として採用し、`app/build.gradle.kts`
にコメントで根拠を残した。後続 Issue で実 URL が確定したら同既定値を差し替えればよい。

→ PR 本文「確認事項」に開発サーバー URL の正式値を依頼予定。

### Launcher Icon

AGP の自動生成 PNG ランチャーは外部依存（解像度別 PNG 生成）が必要なため、本 Issue では
adaptive icon（`mipmap-anydpi-v26/ic_launcher.xml`）+ vector foreground のみで構成した。
minSdk = 26 なので legacy mipmap-{m,h,xh,xxh,xxxh}dpi は不要。Material3 の lint 警告は出ない。

### AppShell の AppConfig 取得方法

`AppShell` Composable は `AppShellViewModel`（`@HiltViewModel` で `AppConfig` を inject）
経由で `mockMode` を読む。当初は `EntryPoint` 直接取得も検討したが、Hilt ViewModel 経由の
方が Compose UI テストでフェイク Hilt graph に置き換えやすいため採用した。

### kapt ではなく KSP を採用

Hilt 2.52 + Kotlin 2.0.21 の組み合わせは KSP 2.0.21-1.0.28 を採用。kapt は K2 で deprecate
予定であり、ビルド速度も KSP の方が安定して速い。design.md には kapt とも KSP とも明示が
なかったため、本 Issue で KSP を選択。

### compileSdk 35（36 ではない）

ローカル環境に android-36 SDK が未配置のため、design.md の「compileSdk 35 を上限とする
（ローカル環境制約）」に従い 35 を採用。AGP 8.7.3 はこれと整合する。

### Test の Turbine 利用

`FakeItemRepositoryTest` で 1 ケース、`runTest` の中で Flow 終端まで読む際に当初
`test {} returns value` 形式で書こうとしたが、Turbine の `test {}` は `Unit` を返すため
コンパイルエラーになる。assertion を `test {}` ブロック内に閉じ込める形に修正した。

## 確認事項（PR 本文に転記する）

- **開発サーバー URL の正式値**: 本 Issue では `https://dev.feedman.example.com` を
  プレースホルダとして採用。実際の dev URL を後続 Issue（または `gradle.properties` への
  追記）で差し替える前提
- **未配置の依存（Retrofit / OkHttp / kotlinx.serialization / Coil / Paging）**:
  Version Catalog では宣言済みだが、`app/build.gradle.kts` の `dependencies {}` には
  まだ追加していない（本 Issue のスコープ「未使用 placeholder」と判断）。後続 Issue で
  必要に応じて追加する
- **`core/auth/AuthRepository`**: インターフェース宣言のみ。メソッドは未定義（後続 Issue で
  `exchangeAuthCode` / `refresh` / `revoke` 等が追加される予定）
- **Compose UI / instrumented テスト**: `app/src/androidTest/` はディレクトリ確保のみ
  （NFR 2.2 の指針通り）

## 派生タスク候補

- 開発サーバー URL 確定後の Gradle プロパティ既定値更新
- `core/network` の Retrofit / OkHttp / kotlinx.serialization 統合（後続 Issue）
- `feature/login` 本実装（Google OAuth + Custom Tabs + PKCE）
- デザインシステム本実装（oklch → ARGB トークン / Indigo accent）

## ビルド結果（最終）

```
$ ./gradlew build
...
BUILD SUCCESSFUL in 50s
119 actionable tasks: 17 executed, 2 from cache, 100 up-to-date
```

- compile / Android Lint（`lint`）/ JVM unit tests（`testDebugUnitTest` + `testReleaseUnitTest`）
  が全てパス
- 単体テスト 3 ファイル / 7 テスト全て成功

STATUS: complete
