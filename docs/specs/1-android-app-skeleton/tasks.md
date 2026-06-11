# Implementation Plan

> 本タスク列は `design.md` の File Structure Plan / Components and Interfaces に対応する。
> 各タスクは 1 commit 単位で独立完了可能。タスクは直列実行を基本とする（Gradle 基盤 → パッケージ骨格 → Hilt → 起動経路 → README の順で依存）。

- [ ] 1. Gradle / wrapper / Version Catalog の最小基盤を配置する
  - リポジトリ直下に `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` / `.gitignore` を新規作成する
  - `gradle/wrapper/gradle-wrapper.properties` の `distributionUrl` を最新安定版 Gradle に固定し、`gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` をコミットする（NFR 3.1）
  - `gradle/libs.versions.toml` を新規作成し、Kotlin / AGP / Compose BOM / Material3 / Hilt / Coroutines / Retrofit / OkHttp / kotlinx.serialization / Coil / Paging 3 / JUnit / Turbine の **最新安定版（GA）** を `[versions]` / `[libraries]` / `[plugins]` で宣言する（Req 1.7, NFR 1.1, NFR 1.2）
  - 例外（安定版不在）の場合は TOML コメントで理由を残す（NFR 1.2）
  - 機密値は埋め込まない（Req 5.5）
  - _Requirements: 1.4, 1.5, 1.7, 5.5, NFR 1.1, NFR 1.2, NFR 3.1_

- [ ] 2. `:app` モジュールの Android / Kotlin / Compose ビルド設定と Gradle プロパティ → BuildConfig マッピングを定義する
  - `app/build.gradle.kts` を新規作成し、`com.android.application` / `kotlin-android` / `kotlin-kapt` / `com.google.dagger.hilt.android` プラグインを適用する
  - `android { namespace = "com.feedman.android"; defaultConfig { applicationId = "com.feedman.android"; minSdk = 26; compileSdk = 35; targetSdk = 35 } }` を設定する
  - `kotlin { jvmToolchain(17) }` および `buildFeatures { compose = true; buildConfig = true }` を有効化する
  - `findProperty("feedman.baseUrl")` の値（未指定時は開発サーバー既定 URL）を `buildConfigField("String", "BASE_URL", ...)` に注入する。既定値の根拠をコメントで明示する
  - `findProperty("feedman.mockMode")?.toString()?.toBoolean() ?: false` を `buildConfigField("boolean", "MOCK_MODE", ...)` に注入する
  - `proguard-rules.pro`（空）と `app/src/main/AndroidManifest.xml`（FeedmanApplication / MainActivity / `INTERNET` 権限）、最小 `res/values/strings.xml` / `res/values/themes.xml` / ランチャーアイコン、`app/src/androidTest/.gitkeep` を配置する（NFR 2.2）
  - _Requirements: 1.2, 1.3, 1.4, 2.5, 5.1, 5.2, 5.3, 5.4, NFR 2.1, NFR 2.2_

- [ ] 3. `core/*` / `feature/*` / `shell` / `di` のパッケージ骨格を placeholder 込みで配置する
  - `app/src/main/kotlin/com/feedman/android/` 配下に `core/{model,network,auth,data,designsystem,ui}` の 6 サブパッケージを作成する（Req 2.2）
  - `feature/{login,timeline,feed,articledetail,starred,search,registerfeed,subscriptionsettings,account}` の 9 サブパッケージを作成する（Req 2.3）
  - `shell` / `di` パッケージを作成する（Req 2.4）
  - 具体実装を持たないパッケージ（`core/network`, `core/ui`, `feature/feed` 〜 `feature/account` 等）には `package-info.kt`（KDoc 付き Kotlin file）を 1 個ずつ配置して空ディレクトリを回避する（Req 2.7）
  - `core/model/AppConfig.kt`（data class）、`core/model/Item.kt`（`ItemSummary` data class）、`core/designsystem/{FeedmanTheme,FeedmanColors}.kt`（最小 Material3 ラッパ）、`core/auth/AuthRepository.kt`（interface 宣言のみ）を配置する
  - `feature/*` から `core/*` のみ参照する依存方向を守る（Req 2.6）
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6, 2.7_

- [ ] 4. Hilt 統合と `ItemRepository` / Fake 実装 / DI module を実装する
  - `FeedmanApplication.kt`（`@HiltAndroidApp`）と `MainActivity.kt`（`@AndroidEntryPoint`、`setContent { FeedmanTheme { AppShell() } }`）を作成する（Req 2.5）
  - `core/data/ItemRepository.kt` に `interface ItemRepository { fun observeTimeline(): Flow<List<ItemSummary>> }` を定義する（Req 3.2）
  - `core/data/fake/FakeItemRepository.kt` に静的モック記事を `flowOf(...)` で返す `@Singleton class FakeItemRepository @Inject constructor() : ItemRepository` を実装する（Req 3.2, 4.4）
  - `di/RepositoryModule.kt` で `@Binds @Singleton fun bindItemRepository(impl: FakeItemRepository): ItemRepository` を宣言する（Req 3.1, 3.3, 3.4）
  - `di/AppConfigModule.kt` で `@Provides @Singleton fun provideAppConfig(): AppConfig = AppConfig(BuildConfig.BASE_URL, BuildConfig.MOCK_MODE)` を宣言する（Req 5.1, 5.2）
  - _Requirements: 2.5, 3.1, 3.2, 3.3, 3.4, 4.4, 5.1, 5.2_
  - _Depends: 3_

- [ ] 5. 起動経路（ログイン placeholder / モックモードのドロワーシェル + タイムライン）を実装する
  - `feature/login/LoginPlaceholderScreen.kt` に静的 Composable を実装する（無効化ボタン + 静的文言）。`AuthRepository` を呼ばないこと（Req 4.2）
  - `feature/timeline/TimelineViewModel.kt` に `@HiltViewModel class TimelineViewModel @Inject constructor(repo: ItemRepository)` を実装し、`StateFlow<TimelineUiState>` を公開する（Req 4.4）
  - `feature/timeline/TimelineScreen.kt` に `TimelineUiState` を受け取って LazyColumn で描画する stateless Composable を実装する
  - `shell/Navigation.kt` に `androidx.navigation:navigation-compose` の `NavHost` を作り、`timeline` ルートのみ登録する
  - `shell/DrawerContent.kt` にタイムライン項目のみ持つ最小ドロワーを実装する
  - `shell/AppShell.kt` で `AppConfig` を Hilt 経由で取得し、`mockMode == true` → `ModalNavigationDrawer + Navigation()`、`mockMode == false` → `LoginPlaceholderScreen()` に分岐する（Req 4.1, 4.3, 4.5）
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  - _Depends: 4_

- [ ] 6. JVM 単体テストを追加し、`./gradlew build` を成功させる
  - `app/src/test/kotlin/com/feedman/android/core/data/fake/FakeItemRepositoryTest.kt` を新規作成し、`runTest` + Turbine で `observeTimeline()` が 1 件以上の `ItemSummary` を emit することを検証する（Req 1.6, 3.2, 4.4）
  - 必要に応じて `TimelineViewModelTest.kt`（Fake 注入 → `TimelineUiState.items` の検証）を追加する
  - `./gradlew build` をローカル実行し、compile / lint / unit test の exit code 0 を確認する（Req 1.1）
  - _Requirements: 1.1, 1.6, NFR 2.1_
  - _Depends: 4_

- [ ] 7. README にビルド手順と Gradle プロパティの指定方法を追記する
  - 既存 `README.md` に「ビルド手順」節を追加し、JDK 17 toolchain・`./gradlew build` 実行コマンド・`feedman.mockMode=true` でのモックモード起動方法を記述する（Req 6.1）
  - 同節に `feedman.baseUrl` / `feedman.mockMode` を `-P` フラグまたは `gradle.properties` で指定する方法を記述する（Req 6.2）
  - 機密値（実トークン等）をコミットしない方針を明記する（Req 5.5 を補強）
  - _Requirements: 6.1, 6.2_
