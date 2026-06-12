package com.feedman.android.core.designsystem

import kotlinx.coroutines.flow.Flow

/**
 * テーマモードの永続化を抽象化するリポジトリ（Issue #25 / Req 3 / NFR 3.2）。
 *
 * - [observe] は現在値の [Flow] を返し、変更時に下流へ通知する（Req 3.3 の再コンポジション駆動）。
 *   永続化が空 / 不正値の場合は [ThemeMode.DEFAULT]（端末追従）を返す（Req 3.2 / 3.6）。
 * - [setMode] は新しい値を永続化する（Req 3.4）。プロセス終了後の次回起動で [observe] の
 *   初期値として復元される。
 *
 * 実装はプロダクション向けに DataStore Preferences 裏付け版、テスト向けに in-memory 版の 2 系統を持つ。
 */
interface ThemeModeRepository {
    fun observe(): Flow<ThemeMode>
    suspend fun setMode(mode: ThemeMode)
}
