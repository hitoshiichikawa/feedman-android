package com.feedman.android.core.model

/**
 * Test-local helper for loading fixture JSON files from `src/test/resources/fixtures/`.
 *
 * Kept inside the model test package (rather than a shared `core/network` location) so
 * that Issue #15 stays within the boundary declared by requirements.md NFR 1.1 (changes
 * limited to `core/model` and `app/src/test/`). A repository-wide common Json setup is
 * the responsibility of Issue #17 (`core/network`).
 */
internal object FixtureLoader {
    /**
     * Reads the fixture JSON at `app/src/test/resources/fixtures/<name>`.
     *
     * Uses the classloader so that the fixture is reachable from JVM unit tests without
     * depending on the working directory.
     */
    fun load(name: String): String {
        val path = "fixtures/$name"
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Fixture not found on the test classpath: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
