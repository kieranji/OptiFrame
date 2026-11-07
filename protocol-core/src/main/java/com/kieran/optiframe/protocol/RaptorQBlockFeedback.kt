package com.kieran.optiframe.protocol

/** Application-level result sent back to the sender after block reception. */
data class RaptorQBlockFeedback(
    val sourceBlockNumber: Int,
    val blockDecoded: Boolean,
    val additionalRepairSymbols: Int = 0,
    val uniqueSymbolsReceived: Int
) {
    init {
        require(sourceBlockNumber in 0..0xFFFF)
        require(additionalRepairSymbols >= 0)
        require(uniqueSymbolsReceived >= 0)
        require(blockDecoded.not() || additionalRepairSymbols == 0) {
            "A decoded block cannot request repairs"
        }
    }
}
