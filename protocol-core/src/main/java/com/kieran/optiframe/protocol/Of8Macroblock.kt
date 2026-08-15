package com.kieran.optiframe.protocol

data class Of8Macroblock(
    val header: Of8MacroblockHeader,
    val payload: ByteArray
) {
    init {
        require(
            payload.size == ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
        ) {
            "OF8 payload must be exactly " +
                    "${ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES} bytes, " +
                    "but was ${payload.size}"
        }
    }
}