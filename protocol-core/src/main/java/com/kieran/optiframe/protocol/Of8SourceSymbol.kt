package com.kieran.optiframe.protocol

class Of8SourceSymbol(
    val esi: Int,
    bytes: ByteArray
) {

    val bytes: ByteArray =
        bytes.copyOf()

    init {
        require(esi >= 0) {
            "ESI must be non-negative"
        }

        require(
            this.bytes.size ==
                    ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
        ) {
            "OF8 source symbol must contain exactly " +
                    "${ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES} bytes"
        }
    }
}