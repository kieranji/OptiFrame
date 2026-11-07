package com.kieran.optiframe.protocol

/** One RaptorQ encoding symbol, either systematic source or repair data. */
class RaptorQSymbol(
    val esi: Long,
    bytes: ByteArray,
    val kind: Kind
) {
    private val symbolBytes: ByteArray = bytes.copyOf()

    val bytes: ByteArray
        get() = symbolBytes.copyOf()

    init {
        require(esi in 0..MAX_ESI) {
            "ESI must fit the RFC 6330 24-bit field: $esi"
        }
        require(symbolBytes.size == ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) {
            "RaptorQ symbol must contain exactly " +
                "${ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES} bytes"
        }
    }

    enum class Kind {
        SOURCE,
        REPAIR
    }

    companion object {
        const val MAX_ESI: Long = 0x00FF_FFFFL
    }
}
