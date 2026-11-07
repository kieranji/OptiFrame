package com.kieran.optiframe.protocol

/** A successfully recovered set of systematic source symbols for one file block. */
class RaptorQDecodedSourceBlock(
    val sourceBlockNumber: Int,
    sourceSymbols: List<Of8SourceSymbol>
) {
    val sourceSymbols: List<Of8SourceSymbol> = sourceSymbols.toList()

    init {
        require(sourceBlockNumber in 0..0xFFFF) {
            "sourceBlockNumber must fit uint16"
        }
        require(this.sourceSymbols.isNotEmpty()) {
            "Decoded source block must contain at least one source symbol"
        }
        require(this.sourceSymbols.size <= ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK) {
            "Decoded source block exceeds maximum K"
        }
        this.sourceSymbols.forEachIndexed { index, symbol ->
            require(symbol.esi == index) {
                "Recovered systematic symbol at index $index has ESI ${symbol.esi}"
            }
        }
    }

    val k: Int
        get() = sourceSymbols.size
}
