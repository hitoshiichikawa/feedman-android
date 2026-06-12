package com.feedman.android.core.designsystem

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ThemeModeRepository] の DataStore Preferences 裏付け実装（Issue #25 / Req 3.4, 3.6）。
 *
 * - [Preferences] への保存値は [ThemeMode.name] の文字列。enum 名が将来変わった場合、
 *   未知の名前は不正値として `ThemeMode.DEFAULT` にフォールバックする（Req 3.6）。
 * - 読み出し時の I/O 例外は [Flow.catch] で握り、`emit(emptyPreferences)` 相当を経由して
 *   既定値を返す（Req 3.6: 読み出し失敗時もアプリ起動を継続）。
 * - 書き込みは [DataStore.edit] による atomic 更新で、並行書き込みは DataStore 内部の
 *   `actor` で直列化される。
 *
 * [DataStore] そのものを DI で受け取ることで、Android Context への直接依存を切り、
 * JVM 単体テスト（[androidx.datastore.preferences.core.PreferenceDataStoreFactory] +
 * tmp dir）からも実装そのものを起動できる構成にする（NFR 3.2）。
 */
@Singleton
class DataStoreThemeModeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ThemeModeRepository {

    override fun observe(): Flow<ThemeMode> = dataStore.data
        .catch { /* Req 3.6: 永続化読み出しの失敗時は既定値で復帰 */ emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[KEY_THEME_MODE] ?: return@map ThemeMode.DEFAULT
            try {
                ThemeMode.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                // 未知の文字列（旧バージョン / 手動編集等）は安全側に倒し既定値へ。
                ThemeMode.DEFAULT
            }
        }

    override suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    private companion object {
        /** Preferences のキー名。スキーマ互換性のため不用意に変えないこと。 */
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
