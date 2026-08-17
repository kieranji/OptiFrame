package com.kieran.optiframe.protocol

class Of8SourceBlock(
    val blockNumber: Int,
    val originalByteCount: Int,
    val symbols: List<Of8SourceSymbol>
) {

    init {
        require(blockNumber >= 0) {
            "blockNumber must be non-negative"
        }

        require(
            originalByteCount >= 0
        ) {
            "originalByteCount must be non-negative"
        }

        require(
            symbols.size <=
                    ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK
        ) {
            "Source block exceeds maximum K"
        }

        require(
            originalByteCount <=
                    ProtocolConstants.OF8_SOURCE_BLOCK_MAX_BYTES
        ) {
            "Source block contains too many original bytes"
        }

        symbols.forEachIndexed { index, symbol ->

            require(symbol.esi == index) {
                "Systematic source symbol ESI must equal " +
                        "its index inside the source block"
            }
        }
    }

    val k: Int
        get() = symbols.size
}