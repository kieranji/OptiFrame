package com.kieran.optiframe.protocol

/**
 * Applies receiver feedback to the file scheduler and controls block advance.
 * Incomplete feedback must request at least one additional repair symbol.
 */
class RaptorQFileSendController(
    private val scheduler: RaptorQFileTransmissionScheduler
) {
    val isComplete: Boolean
        get() = scheduler.isComplete

    val currentSourceBlockNumber: Int?
        get() = scheduler.currentSourceBlockNumber

    val nextSymbolKind: RaptorQSymbol.Kind?
        get() = scheduler.nextSymbolKind

    val progress: RaptorQFileSendProgress
        get() = RaptorQFileSendProgress(
            completedBlocks = scheduler.completedBlockCount,
            totalBlocks = scheduler.totalSourceBlockCount,
            currentBlockNumber = scheduler.currentSourceBlockNumber,
            isComplete = scheduler.isComplete,
            symbolsSentInCurrentBlock = scheduler.currentBlockSymbolsSent,
            symbolsSentInCompletedBlocks = scheduler.completedSymbolsSent
        )

    fun nextTransmission(): RaptorQTransmissionItem? = scheduler.next()

    fun prepareNextBlock() = scheduler.prepareNextBlock()

    private fun applyFeedback(
        sourceBlockNumber: Int,
        blockDecoded: Boolean,
        additionalRepairSymbols: Int = 0
    ) {
        require(additionalRepairSymbols >= 0) {
            "additionalRepairSymbols must be non-negative"
        }
        check(!scheduler.isComplete) { "All source blocks are already complete" }
        require(sourceBlockNumber == scheduler.currentSourceBlockNumber) {
            "Feedback is for block $sourceBlockNumber; current block is " +
                scheduler.currentSourceBlockNumber
        }
        if (blockDecoded) {
            require(additionalRepairSymbols == 0) {
                "Decoded block feedback cannot request more repair symbols"
            }
            scheduler.markCurrentBlockComplete()
        } else {
            require(additionalRepairSymbols > 0) {
                "Incomplete block feedback must request repair symbols"
            }
            scheduler.requestMoreRepairSymbols(additionalRepairSymbols)
        }
    }

    fun applyFeedback(feedback: RaptorQBlockFeedback) {
        // SAFE30 repeats a logical frame and control feedback can also be
        // retried. A stale positive ACK for an already completed block is
        // therefore idempotent; repair requests and future-block ACKs are not.
        if (feedback.blockDecoded &&
            feedback.sourceBlockNumber in 0 until scheduler.completedBlockCount
        ) {
            return
        }
        check(!scheduler.isComplete) { "All source blocks are already complete" }
        require(feedback.sourceBlockNumber == scheduler.currentSourceBlockNumber) {
            "Feedback is for block ${feedback.sourceBlockNumber}; current block is " +
                scheduler.currentSourceBlockNumber
        }
        require(feedback.uniqueSymbolsReceived <= scheduler.currentBlockSymbolsSent) {
            "Receiver reports more unique symbols than have been sent"
        }
        if (feedback.blockDecoded) {
            require(feedback.uniqueSymbolsReceived >= scheduler.currentBlockSourceSymbolCount) {
                "Decoded feedback reports fewer unique symbols than source symbols"
            }
        }
        applyFeedback(
            sourceBlockNumber = feedback.sourceBlockNumber,
            blockDecoded = feedback.blockDecoded,
            additionalRepairSymbols = feedback.additionalRepairSymbols
        )
    }
}
