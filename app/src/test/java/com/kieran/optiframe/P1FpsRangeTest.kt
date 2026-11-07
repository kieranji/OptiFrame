package com.kieran.optiframe

import com.kieran.optiframe.ui.optical.P1CameraThreeAControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P1FpsRangeTest {
    @Test
    fun `SAFE30 selects a range containing 30 instead of forcing 60`() {
        assertEquals(
            15..30,
            P1CameraThreeAControl.chooseFpsRange(listOf(15..30, 60..60), 30)
        )
    }

    @Test
    fun `fixed target range takes priority`() {
        assertEquals(
            30..30,
            P1CameraThreeAControl.chooseFpsRange(listOf(15..30, 30..30), 30)
        )
    }

    @Test
    fun `fixed 60 fps range takes priority`() {
        assertEquals(
            60..60,
            P1CameraThreeAControl.chooseFpsRange(listOf(15..30, 30..60, 60..60), 60)
        )
    }

    @Test
    fun `selects the supported range containing 60 fps`() {
        assertEquals(
            30..60,
            P1CameraThreeAControl.chooseFpsRange(listOf(15..30, 30..60), 60)
        )
    }

    @Test
    fun `falls back to the range with the highest upper bound`() {
        assertEquals(
            15..30,
            P1CameraThreeAControl.chooseFpsRange(listOf(15..30), 60)
        )
    }

    @Test
    fun `prefers the containing range with the highest lower bound`() {
        assertEquals(
            15..30,
            P1CameraThreeAControl.chooseFpsRange(listOf(7..30, 15..30), 30)
        )
    }

    @Test
    fun `empty supported range list returns null`() {
        assertNull(P1CameraThreeAControl.chooseFpsRange(emptyList(), 30))
    }
}
