package com.feedman.android.core.designsystem

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [DataStoreThemeModeRepository] の保存 / 読み出し / フォールバック挙動を JVM 単体テスト
 * から担保する（Issue #25 / Req 3.2 / 3.4 / 3.6 / NFR 3.2）。
 *
 * `PreferenceDataStoreFactory` を tmp dir 上に作って実物の DataStore を起動する。Robolectric
 * や Android Context は不要（NFR 3.2）。
 */
class DataStoreThemeModeRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dispatcher: kotlinx.coroutines.test.TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: DataStoreThemeModeRepository

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        testScope = TestScope(dispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tempFolder.root, "test_theme.preferences_pb") },
        )
        repo = DataStoreThemeModeRepository(dataStore)
    }

    @After
    fun tearDown() {
        // TestScope はテスト終了時に自動キャンセルされる。
    }

    @Test
    fun `Req 3_2 default is FOLLOW_SYSTEM when datastore is empty`() = runTest(dispatcher) {
        repo.observe().test {
            assertEquals(ThemeMode.FOLLOW_SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Req 3_4 setMode persists value across new repository instance`() = runTest(dispatcher) {
        // Arrange: 値を保存
        repo.setMode(ThemeMode.DARK)

        // Act: 同じ DataStore を裏付けにした新しいリポジトリインスタンスから読み出す
        val newRepo = DataStoreThemeModeRepository(dataStore)

        // Assert
        newRepo.observe().test {
            assertEquals(ThemeMode.DARK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Req 3_4 setMode updates emit to active observers`() = runTest(dispatcher) {
        repo.observe().test {
            assertEquals(ThemeMode.FOLLOW_SYSTEM, awaitItem())
            repo.setMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitItem())
            repo.setMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Req 3_6 unknown raw value falls back to FOLLOW_SYSTEM`() = runTest(dispatcher) {
        // Arrange: DataStore に未知の文字列を書き込む（旧バージョンや手動編集を想定）
        val key = stringPreferencesKey("theme_mode")
        dataStore.edit { it[key] = "PURPLE_RAIN" }

        // Act / Assert
        repo.observe().test {
            assertEquals(ThemeMode.FOLLOW_SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
