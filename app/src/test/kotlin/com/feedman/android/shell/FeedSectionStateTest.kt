package com.feedman.android.shell

import com.feedman.android.core.data.SubscriptionLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FeedSectionState.from] 純粋関数の単体テスト
 * （Issue #39 / Req 2.1, 2.2, 2.5, 2.6）。
 *
 * `SubscriptionLoadState` と現在の `hasRows` から UI 表示状態を導出する分岐を、
 * Composable 起動なしで検証する。
 */
class FeedSectionStateTest {

    @Test
    fun `Idle のとき Idle を返す`() {
        assertEquals(
            FeedSectionState.Idle,
            FeedSectionState.from(SubscriptionLoadState.Idle, hasRows = false),
        )
        assertEquals(
            FeedSectionState.Idle,
            FeedSectionState.from(SubscriptionLoadState.Idle, hasRows = true),
        )
    }

    @Test
    fun `Loading かつ rows が空のとき Loading を返す_Req 2_5`() {
        assertEquals(
            FeedSectionState.Loading,
            FeedSectionState.from(SubscriptionLoadState.Loading, hasRows = false),
        )
    }

    @Test
    fun `Loading かつ rows があるとき silent refresh として Success を返す`() {
        assertEquals(
            FeedSectionState.Success,
            FeedSectionState.from(SubscriptionLoadState.Loading, hasRows = true),
        )
    }

    @Test
    fun `Success のとき rows の有無にかかわらず Success を返す`() {
        assertEquals(
            FeedSectionState.Success,
            FeedSectionState.from(SubscriptionLoadState.Success, hasRows = false),
        )
        assertEquals(
            FeedSectionState.Success,
            FeedSectionState.from(SubscriptionLoadState.Success, hasRows = true),
        )
    }

    @Test
    fun `Error のとき rows の有無にかかわらず Error を返し message を保持する_Req 2_1_2_2_2_6`() {
        val source = SubscriptionLoadState.Error(
            message = "ネットワーク失敗",
            code = "NETWORK_ERROR",
        )

        val withoutRows = FeedSectionState.from(source, hasRows = false)
        val withRows = FeedSectionState.from(source, hasRows = true)

        assertTrue(withoutRows is FeedSectionState.Error)
        assertEquals("ネットワーク失敗", (withoutRows as FeedSectionState.Error).message)
        assertTrue(withRows is FeedSectionState.Error)
        assertEquals("ネットワーク失敗", (withRows as FeedSectionState.Error).message)
    }
}
