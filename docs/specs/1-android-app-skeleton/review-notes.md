# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-06-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-1-impl-android-app-skeleton
- HEAD commit: 2d187f6a76301a0f1ac65cc80fde8b9df5af7c12
- Compared to: main..HEAD（52 files changed, +1725 / -2）
- Feature Flag Protocol: opt-out（採否確認済み。flag 観点は適用なし）

## Verified Requirements

- 1.1 — `impl-notes.md` の「ビルド結果（最終）」で `./gradlew build` が `BUILD SUCCESSFUL in 50s` / 119 tasks / 単体テスト 7 件全成功と記録。compile + lint + JVM 単体テストが exit 0
- 1.2 — `app/build.gradle.kts`: `namespace = "com.feedman.android"` / `applicationId = "com.feedman.android"` / `minSdk = 26`
- 1.3 — `app/build.gradle.kts`: `kotlin { jvmToolchain(17) }` + `compileOptions { source/targetCompatibility = VERSION_17 }`、`androidx-compose-bom` / `androidx-compose-material3` 依存
- 1.4 — `settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts`（全て Kotlin DSL）+ `gradle/libs.versions.toml`
- 1.5 — `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.{jar,properties}` が commit 済み
- 1.6 — `FakeItemRepositoryTest`（3 テスト）/ `TimelineViewModelTest`（2 テスト）/ `AppConfigTest`（2 テスト）= 計 7 件
- 1.7 — `gradle/libs.versions.toml` 冒頭で GA 安定版方針をコメント化、列挙された全 version が GA（Kotlin 2.0.21 / AGP 8.7.3 / Hilt 2.52 / Compose BOM 2024.10.01 等）
- 2.1 — `app/src/main/kotlin/com/feedman/android/` を `sourceSets` で有効化、`FeedmanApplication.kt` 等がルート直下
- 2.2 — `core/{model,network,auth,data,designsystem,ui}` の 6 サブパッケージを `ls` で確認
- 2.3 — `feature/{login,timeline,feed,articledetail,starred,search,registerfeed,subscriptionsettings,account}` の 9 サブパッケージを `ls` で確認
- 2.4 — `shell/{AppShell,DrawerContent,Navigation}.kt` + `di/{RepositoryModule,AppConfigModule}.kt`
- 2.5 — `FeedmanApplication`（`@HiltAndroidApp`）+ `MainActivity`（`@AndroidEntryPoint`、`setContent { FeedmanTheme { AppShell() } }`）+ `AndroidManifest.xml` に登録
- 2.6 — `grep -r "import com.feedman.android.feature" core/` 結果ゼロ、`feature/*` 配下の cross-feature import も検出されず
- 2.7 — 全 placeholder package に `Placeholder.kt`（KDoc 付き）配置
- 3.1 — Hilt（`hilt-android` + `hilt-compiler` via KSP）導入、`di/` に module 集約（`@InstallIn(SingletonComponent::class)`）
- 3.2 — `core/data/ItemRepository.kt`（interface）+ `core/data/fake/FakeItemRepository.kt`（実装）/ `FakeItemRepositoryTest` の 3 ケースで担保
- 3.3 — `RepositoryModule` の `@Binds @Singleton` が `FakeItemRepository` を `ItemRepository` にバインド
- 3.4 — `FakeItemRepository` は `@Singleton class @Inject constructor()` で Hilt 管理、手動 `object` シングルトンなし
- 4.1 — `AppShell.kt`: `if (viewModel.appConfig.mockMode) MockModeShell() else LoginPlaceholderScreen()` 分岐
- 4.2 — `LoginPlaceholderScreen.kt`: `AuthRepository` を import / inject せず、静的 Composable + `Button(onClick = {}, enabled = false)`
- 4.3 — `MockModeShell`: `ModalNavigationDrawer(drawerContent = { DrawerContent() }) { Scaffold(topBar) { Navigation() } }`、`Navigation.kt` は `startDestination = "timeline"`
- 4.4 — `TimelineViewModel` が `@HiltViewModel` で `ItemRepository` を inject、`StateFlow<TimelineUiState>` を公開 / `TimelineViewModelTest` が ViewModel が Fake 由来 items を公開することを検証
- 4.5 — `AppConfigModule` が `BuildConfig.MOCK_MODE`（既定 false）を `AppConfig.mockMode` に反映、`AppShell` 分岐で `LoginPlaceholderScreen()` に遷移
- 5.1 — `app/build.gradle.kts`: `buildConfigField("String", "BASE_URL", "\"$defaultBaseUrl\"")`、`defaultBaseUrl = findProperty("feedman.baseUrl") ?: "https://dev.feedman.example.com"`
- 5.2 — `buildConfigField("boolean", "MOCK_MODE", defaultMockMode.toString())`、`defaultMockMode = findProperty("feedman.mockMode")?.toString()?.toBoolean() ?: false`
- 5.3 — `defaultBaseUrl` の既定値 `https://dev.feedman.example.com` を採用（コメントで根拠を明示。実 dev URL 確定は後続 Issue 範囲との判断は impl-notes に記録）
- 5.4 — `defaultMockMode ?: false`（実装と `AppConfigTest` の equality / default 検証で挙動を保証）
- 5.5 — Version Catalog / source / `gradle.properties` に実 API キー・トークンなし
- 6.1 — `README.md` 「ビルド・実行」節に JDK 17 / `./gradlew build` / モックモード起動例を記述
- 6.2 — 同節に Gradle プロパティ表 + `-P` フラグ + `gradle.properties` 記法を記述
- NFR 1.1 — `gradle/libs.versions.toml` で全主要依存を GA 安定版で宣言
- NFR 1.2 — 安定版不在ケースは存在せず、ポリシーをコメントで明記
- NFR 2.1 — `sourceSets` で `app/src/test/kotlin` を有効化、`./gradlew build` から `testDebugUnitTest` / `testReleaseUnitTest` がパス
- NFR 2.2 — `app/src/androidTest/.gitkeep` のみ配置（テストファイルなし / CI 必須化なし）
- NFR 3.1 — `gradle/wrapper/gradle-wrapper.properties` の `distributionUrl` を `gradle-8.10.2-bin.zip` に固定

## Findings

なし

## Summary

requirements.md の全 numeric ID（Req 1–6 / NFR 1–3）について、対応する実装またはテストが
最新 commit に存在することを確認。tasks.md の `_Requirements:_` カバレッジ、design.md の File
Structure Plan、依存方向（`feature/* → core/*`、cross-feature 禁止）、テストの近傍配置・
命名・モック方針（`runTest` + Turbine + `Dispatchers.setMain`）はいずれも CLAUDE.md の
Kotlin / Android 規約に整合。境界逸脱・AC 未カバー・missing test のいずれも検出されず。

RESULT: approve
