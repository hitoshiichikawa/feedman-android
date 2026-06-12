package com.feedman.android.core.network

/**
 * Issue #16 用の fixture ローダ。
 *
 * Issue #15 が `core/model` 配下のテストで使用している同名ヘルパー（`core/model/FixtureLoader.kt`）と
 * 機能的に等価だが、テストクラスのパッケージ境界 (`core/network`) に閉じておくため再宣言する。
 * 共通 fixture ローダの本格集約は Issue #17 以降の領分（NFR 3.1）。
 */
internal object FixtureLoader {
    /**
     * `app/src/test/resources/fixtures/<name>` を classpath から読み込んで文字列で返す。
     */
    fun load(name: String): String {
        val path = "fixtures/$name"
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Fixture not found on the test classpath: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
