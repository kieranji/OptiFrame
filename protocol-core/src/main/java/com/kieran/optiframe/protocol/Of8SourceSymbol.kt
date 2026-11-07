package com.kieran.optiframe.protocol

class Of8SourceSymbol private constructor(
    val esi: Int,
    val bytes: ByteArray,
    @Suppress("UNUSED_PARAMETER") owned: Unit
) {
    constructor(esi: Int, bytes: ByteArray) : this(esi, bytes.copyOf(), Unit)

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

    companion object {
        /** Takes ownership of a freshly allocated symbol buffer. */
        internal fun takeOwnership(esi: Int, bytes: ByteArray): Of8SourceSymbol =
            Of8SourceSymbol(esi, bytes, Unit)
    }
}
