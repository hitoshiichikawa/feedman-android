plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Default base URL when `feedman.baseUrl` Gradle property is not provided (Req 5.3).
 * Points to the staging Feedman server. Override at build time:
 * `./gradlew build -Pfeedman.baseUrl=https://example.invalid`
 */
val defaultBaseUrl: String = (findProperty("feedman.baseUrl") as String?)
    ?: "https://stg-feed.market-river.net"

/**
 * Default mockMode when `feedman.mockMode` Gradle property is not provided (Req 5.4).
 * Skeleton ships with mockMode = false so that the login placeholder screen is the default
 * entry point (Req 4.5). Override: `-Pfeedman.mockMode=true`.
 */
val defaultMockMode: Boolean = (findProperty("feedman.mockMode") as String?)
    ?.toBoolean() ?: false

android {
    namespace = "com.feedman.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.feedman.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-skeleton"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Req 5.1 / 5.2 / 5.3 / 5.4: surface Gradle properties as BuildConfig fields.
        buildConfigField("String", "BASE_URL", "\"$defaultBaseUrl\"")
        buildConfigField("boolean", "MOCK_MODE", defaultMockMode.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlin {
        jvmToolchain(17)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDir("src/test/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDir("src/androidTest/kotlin")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    // Chrome Custom Tabs（Issue #37）: 記事の元 URL を Custom Tabs で開くために配線する
    implementation(libs.androidx.browser)

    // Compose BOM-managed
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Serialization (Issue #15: API ドメインモデル decode で利用)
    implementation(libs.kotlinx.serialization.json)

    // Networking (Issue #17: Retrofit + OkHttp + kotlinx.serialization converter)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    // Paging 3 (Issue #18: カーソルページング基盤)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.common)
    // paging-compose: LazyColumn + collectAsLazyPagingItems で PagingData を消費する
    // タイムライン UI（Issue #33 Req 5.1〜5.4）のために配線する。
    implementation(libs.androidx.paging.compose)

    // Coil (Issue #26: data URL の favicon 復号 + Compose 連携)
    // — gradle/libs.versions.toml で coil = 2.7.0 を宣言済み。
    implementation(libs.coil.compose)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // MockWebServer (Issue #17): Retrofit インターフェースをモックせず、実 HTTP 経路で検証する
    testImplementation(libs.mockwebserver)
    // DataStore preferences-core を JVM テストから利用するため別途宣言。
    // 本体 implementation(`datastore-preferences`) は Android 用 artifact であり、
    // 単体テスト（JVM）向けには tmp dir + `PreferenceDataStoreFactory` を使う
    // `datastore-preferences-core` を併用する（Issue #25 NFR 3.2）。
    testImplementation(libs.androidx.datastore.preferences.core)
    // Paging testing utilities（Issue #18）: TestPager で refresh / append / retry を
    // 結合確認するために JVM テスト側にだけ追加する。
    testImplementation(libs.androidx.paging.testing)
}
