package com.feedman.android.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FeedmanDimens] の規定値が SPEC §8 / Issue #25 Req 4 を満たすことを担保する。
 *
 * 角丸 10dp〜16dp / 最小タップ 44dp / アイコン 18dp〜22dp の範囲。
 */
class FeedmanDimensTest {

    private val dimens = FeedmanDimens()

    @Test
    fun `Req 4_1 corner tokens cover 10dp to 16dp range inclusive`() {
        assertTrue("cornerSmall>=10dp", dimens.cornerSmall >= 10.dp)
        assertTrue("cornerLarge<=16dp", dimens.cornerLarge <= 16.dp)
        assertTrue("cornerSmall<=cornerMedium", dimens.cornerSmall <= dimens.cornerMedium)
        assertTrue("cornerMedium<=cornerLarge", dimens.cornerMedium <= dimens.cornerLarge)
    }

    @Test
    fun `Req 4_1 corner tokens include boundary values 10dp and 16dp`() {
        assertEquals(10.dp, dimens.cornerSmall)
        assertEquals(16.dp, dimens.cornerLarge)
    }

    @Test
    fun `Req 4_2 minTapTarget is 44dp`() {
        assertEquals(44.dp, dimens.minTapTarget)
    }

    /** NFR 2.2: 操作可能要素向け公開寸法は 44dp を下回らない。 */
    @Test
    fun `NFR 2_2 minTapTarget meets accessibility minimum 44dp`() {
        assertTrue(dimens.minTapTarget >= 44.dp)
    }

    @Test
    fun `Req 4_3 icon tokens cover 18dp to 22dp range inclusive`() {
        assertTrue("iconSmall>=18dp", dimens.iconSmall >= 18.dp)
        assertTrue("iconLarge<=22dp", dimens.iconLarge <= 22.dp)
        assertTrue("iconSmall<=iconMedium", dimens.iconSmall <= dimens.iconMedium)
        assertTrue("iconMedium<=iconLarge", dimens.iconMedium <= dimens.iconLarge)
    }

    @Test
    fun `Req 4_3 icon tokens include boundary values 18dp and 22dp`() {
        assertEquals(18.dp, dimens.iconSmall)
        assertEquals(22.dp, dimens.iconLarge)
    }
}
