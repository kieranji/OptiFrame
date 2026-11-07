package com.kieran.optiframe.protocol

/** Re-requests a stalled block once per timeout until progress resumes. */
class P1ReceiverStallWatchdog(
    private val stallTimeoutNanos: Long = DEFAULT_STALL_TIMEOUT_NANOS
) {
    private var activeBlock: Int? = null
    private var markNanos = 0L

    init {
        require(stallTimeoutNanos > 0L)
    }

    fun onFrame(
        sourceBlockNumber: Int,
        result: RaptorQOpticalFrameReceiveResult,
        nowNanos: Long
    ) {
        if (result.feedback?.blockDecoded == true) {
            activeBlock = null
            markNanos = nowNanos
        } else if (sourceBlockNumber != activeBlock) {
            activeBlock = sourceBlockNumber
            markNanos = nowNanos
        } else if (result.feedback != null || result.symbolResults.any {
                it.disposition == RaptorQBlockReceiveResult.Disposition.ACCEPTED ||
                    it.disposition == RaptorQBlockReceiveResult.Disposition.BLOCK_COMPLETE
            }
        ) {
            markNanos = nowNanos
        }
    }

    /** Block number to re-request, or null. */
    fun poll(nowNanos: Long): Int? {
        val block = activeBlock ?: return null
        if (nowNanos - markNanos < stallTimeoutNanos) return null
        markNanos = nowNanos
        return block
    }

    companion object {
        const val DEFAULT_STALL_TIMEOUT_NANOS: Long = 1_000_000_000L
    }
}
