package com.kieran.optiframe.protocol

/** Re-requests repairs when an incomplete block stops making progress (e.g. trailing FAST60 frames lost). */
class P1ReceiverStallWatchdog(
    private val stallTimeoutNanos: Long = DEFAULT_STALL_TIMEOUT_NANOS,
    private val maxRequestsPerStall: Int = DEFAULT_MAX_REQUESTS_PER_STALL
) {
    private var activeBlock: Int? = null
    private var markNanos = 0L
    private var requestsThisStall = 0

    init {
        require(stallTimeoutNanos > 0L)
        require(maxRequestsPerStall > 0)
    }

    fun onFrame(
        sourceBlockNumber: Int,
        result: RaptorQOpticalFrameReceiveResult,
        nowNanos: Long
    ) {
        if (result.feedback?.blockDecoded == true) {
            activeBlock = null
            markNanos = nowNanos
            requestsThisStall = 0
        } else if (sourceBlockNumber != activeBlock) {
            activeBlock = sourceBlockNumber
            markNanos = nowNanos
            requestsThisStall = 0
        } else if (result.feedback != null || result.symbolResults.any {
                it.disposition == RaptorQBlockReceiveResult.Disposition.ACCEPTED ||
                    it.disposition == RaptorQBlockReceiveResult.Disposition.BLOCK_COMPLETE
            }
        ) {
            markNanos = nowNanos
            requestsThisStall = 0
        }
    }

    /** Block number to re-request, or null. */
    fun poll(nowNanos: Long): Int? {
        val block = activeBlock ?: return null
        if (nowNanos - markNanos < stallTimeoutNanos ||
            requestsThisStall >= maxRequestsPerStall
        ) {
            return null
        }
        requestsThisStall++
        markNanos = nowNanos
        return block
    }

    companion object {
        const val DEFAULT_STALL_TIMEOUT_NANOS: Long = 1_000_000_000L
        const val DEFAULT_MAX_REQUESTS_PER_STALL: Int = 10
    }
}
