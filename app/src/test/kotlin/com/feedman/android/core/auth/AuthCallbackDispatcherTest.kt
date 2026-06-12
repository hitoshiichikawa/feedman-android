package com.feedman.android.core.auth

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AuthCallbackDispatcher] の単体テスト（Issue #23 Req 3.1）。
 *
 * - dispatch した URI が intents で観測できる
 * - replay = 1 のため、collector 接続前に dispatch しても 1 件だけ再配信される
 *   （cold start ディープリンク起動の再現テスト）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthCallbackDispatcherTest {

    @Test
    fun `Req 3_1 dispatch emits URI to intents flow`() = runTest {
        // Arrange
        val dispatcher = AuthCallbackDispatcher()

        // Act + Assert
        dispatcher.intents.test {
            dispatcher.dispatch("feedman://auth/callback?auth_code=abc")
            assertEquals("feedman://auth/callback?auth_code=abc", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Req 3_1 cold start dispatch is replayed to late collector`() = runTest {
        // Arrange: collector 接続前に dispatch（cold start シナリオ）
        val dispatcher = AuthCallbackDispatcher()
        dispatcher.dispatch("feedman://auth/callback?auth_code=early")

        // Act + Assert: 後から collect しても replay=1 で 1 件届く
        dispatcher.intents.test {
            assertEquals("feedman://auth/callback?auth_code=early", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Req 3_1 multiple dispatch deliveries are observed in order to a connected collector`() = runTest {
        // Arrange
        val dispatcher = AuthCallbackDispatcher()

        // Act + Assert
        dispatcher.intents.test {
            dispatcher.dispatch("feedman://auth/callback?auth_code=one")
            assertEquals("feedman://auth/callback?auth_code=one", awaitItem())
            dispatcher.dispatch("feedman://auth/callback?auth_code=two")
            assertEquals("feedman://auth/callback?auth_code=two", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
