package com.kieran.optiframe.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P1GoodputWindowTest {
    @Test
    fun ratesAreNullUntilTheFirstMinuteHasElapsed() {
        val window = P1GoodputWindow()

        window.onProgress(0L, 0L)
        window.onProgress(59 * SECOND_NANOS, 590L)

        assertNull(window.firstMinuteKibPerSecond)
        assertNull(window.lastMinuteKibPerSecond)
    }

    @Test
    fun constantRateProducesEqualFirstAndLastMinuteRates() {
        val window = P1GoodputWindow()

        window.onProgress(0L, 0L)
        window.onProgress(30 * SECOND_NANOS, 300L)
        window.onProgress(60 * SECOND_NANOS, 600L)

        assertEquals(2.5, window.firstMinuteKibPerSecond!!, 0.000001)
        assertEquals(2.5, window.lastMinuteKibPerSecond!!, 0.000001)
    }

    @Test
    fun slowdownInTheLastMinuteLowersTheLastMinuteRate() {
        val window = P1GoodputWindow()

        window.onProgress(0L, 0L)
        window.onProgress(60 * SECOND_NANOS, 600L)
        window.onProgress(120 * SECOND_NANOS, 900L)

        assertEquals(2.5, window.firstMinuteKibPerSecond!!, 0.000001)
        assertEquals(1.25, window.lastMinuteKibPerSecond!!, 0.000001)
    }

    private companion object {
        const val SECOND_NANOS = 1_000_000_000L
    }
}
