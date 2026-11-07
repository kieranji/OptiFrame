package com.kieran.optiframe.protocol

/** Immutable sender and receiver progress snapshots for UI/reporting. */
data class RaptorQFileSendProgress(
    val completedBlocks: Int,
    val totalBlocks: Int,
    val currentBlockNumber: Int?,
    val isComplete: Boolean,
    val symbolsSentInCurrentBlock: Int,
    val symbolsSentInCompletedBlocks: Int
)

class RaptorQFileReceiveProgress(
    val completedBlocks: Int,
    receivedSymbolsPerBlock: IntArray,
    val totalBlocks: Int,
    val isComplete: Boolean
) {
    private val receivedCounts = receivedSymbolsPerBlock.copyOf()
    val receivedSymbolsPerBlock: IntArray
        get() = receivedCounts.copyOf()
}
