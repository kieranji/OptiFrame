package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class P1ReceiverStallWatchdogTest {
    @Test
    fun pollIsEmptyBeforeTheFirstFrame() {
        assertNull(P1ReceiverStallWatchdog().poll(0L))
    }

    @Test
    fun requestsAtTimeoutAndWaitsAnotherTimeoutBetweenRequests() {
        val watchdog = P1ReceiverStallWatchdog()
        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.DUPLICATE), 0L)

        assertNull(watchdog.poll(999_999_999L))
        assertEquals(0, watchdog.poll(1_000_000_000L))
        assertNull(watchdog.poll(1_999_999_999L))
        assertEquals(0, watchdog.poll(2_000_000_000L))
    }

    @Test
    fun progressResetsTimerButDuplicateFramesDoNot() {
        val watchdog = P1ReceiverStallWatchdog()
        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.DUPLICATE), 0L)
        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.DUPLICATE), 800_000_000L)
        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.ACCEPTED), 900_000_000L)

        assertNull(watchdog.poll(1_800_000_000L))
        assertEquals(0, watchdog.poll(1_900_000_000L))
    }

    @Test
    fun blockDecodedFeedbackClearsActiveBlock() {
        val watchdog = P1ReceiverStallWatchdog()
        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.DUPLICATE), 0L)
        watchdog.onFrame(
            0,
            result(
                RaptorQBlockReceiveResult.Disposition.ALREADY_COMPLETE,
                RaptorQBlockFeedback(0, blockDecoded = true, uniqueSymbolsReceived = 13)
            ),
            100_000_000L
        )

        assertNull(watchdog.poll(2_000_000_000L))
    }

    @Test
    fun aNewBlockResetsTheStallTimer() {
        val watchdog = P1ReceiverStallWatchdog()
        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.DUPLICATE), 0L)
        watchdog.onFrame(1, result(RaptorQBlockReceiveResult.Disposition.DUPLICATE), 900_000_000L)

        assertNull(watchdog.poll(1_899_999_999L))
        assertEquals(1, watchdog.poll(1_900_000_000L))
    }

    @Test
    fun stopsAfterMaximumRequestsUntilProgressResumes() {
        val watchdog = P1ReceiverStallWatchdog(maxRequestsPerStall = 2)
        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.DUPLICATE), 0L)

        assertEquals(0, watchdog.poll(1_000_000_000L))
        assertEquals(0, watchdog.poll(2_000_000_000L))
        assertNull(watchdog.poll(3_000_000_000L))

        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.BLOCK_COMPLETE), 3_100_000_000L)
        assertEquals(0, watchdog.poll(4_100_000_000L))
    }

    @Test
    fun ordinaryRepairFeedbackResetsTheStallTimer() {
        val watchdog = P1ReceiverStallWatchdog()
        watchdog.onFrame(0, result(RaptorQBlockReceiveResult.Disposition.DUPLICATE), 0L)
        watchdog.onFrame(
            0,
            result(
                RaptorQBlockReceiveResult.Disposition.DUPLICATE,
                RaptorQBlockFeedback(
                    sourceBlockNumber = 0,
                    blockDecoded = false,
                    additionalRepairSymbols = 2,
                    uniqueSymbolsReceived = 12
                )
            ),
            900_000_000L
        )

        assertNull(watchdog.poll(1_800_000_000L))
        assertEquals(0, watchdog.poll(1_900_000_000L))
    }

    @Test
    fun constructorRejectsNonPositiveLimits() {
        assertFailsWith<IllegalArgumentException> { P1ReceiverStallWatchdog(stallTimeoutNanos = 0L) }
        assertFailsWith<IllegalArgumentException> { P1ReceiverStallWatchdog(maxRequestsPerStall = 0) }
    }

    private fun result(
        disposition: RaptorQBlockReceiveResult.Disposition,
        feedback: RaptorQBlockFeedback? = null
    ) = RaptorQOpticalFrameReceiveResult(
        symbolResults = listOf(RaptorQBlockReceiveResult(disposition)),
        slotFailures = emptyList(),
        feedback = feedback
    )
}
