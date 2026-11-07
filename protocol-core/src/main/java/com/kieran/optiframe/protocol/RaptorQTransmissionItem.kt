package com.kieran.optiframe.protocol

/** A single encoding symbol scheduled for one source block. */
data class RaptorQTransmissionItem(
    val sourceBlockNumber: Int,
    val symbol: RaptorQSymbol
) {
    init {
        require(sourceBlockNumber in 0..0xFFFF) {
            "sourceBlockNumber must fit uint16"
        }
    }

    val esi: Long
        get() = symbol.esi

    val kind: RaptorQSymbol.Kind
        get() = symbol.kind
}
