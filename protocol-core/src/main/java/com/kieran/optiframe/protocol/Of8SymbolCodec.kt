package com.kieran.optiframe.protocol

object Of8SymbolCodec {

    const val INPUT_BYTES = 336
    const val SYMBOL_COUNT = 896
    const val BITS_PER_SYMBOL = 3

    fun bytesToSymbols(
        bytes: ByteArray
    ): IntArray {

        require(bytes.size == INPUT_BYTES) {
            "OF8 symbol encoding requires exactly " +
                    "$INPUT_BYTES bytes, but got ${bytes.size}"
        }

        val symbols = IntArray(SYMBOL_COUNT)

        var bitIndex = 0

        for (symbolIndex in 0 until SYMBOL_COUNT) {

            var symbol = 0

            repeat(BITS_PER_SYMBOL) {

                val byteIndex = bitIndex / 8
                val bitInsideByte = bitIndex % 8

                // MSB-first:
                // bit 0 of a byte means the 0x80 position.
                val shift =
                    7 - bitInsideByte

                val bit =
                    (bytes[byteIndex].toInt() ushr shift) and 0x01

                symbol =
                    (symbol shl 1) or bit

                bitIndex++
            }

            symbols[symbolIndex] = symbol
        }

        check(bitIndex == INPUT_BYTES * 8)

        return symbols
    }

    fun symbolsToBytes(
        symbols: IntArray
    ): ByteArray {

        require(symbols.size == SYMBOL_COUNT) {
            "OF8 decoding requires exactly " +
                    "$SYMBOL_COUNT symbols, but got ${symbols.size}"
        }

        val result =
            ByteArray(INPUT_BYTES)

        var bitIndex = 0

        for (symbol in symbols) {

            require(symbol in 0..7) {
                "OF8 symbol must be in range 0..7, but got $symbol"
            }

            for (symbolBit in 2 downTo 0) {

                val bit =
                    (symbol ushr symbolBit) and 0x01

                val byteIndex =
                    bitIndex / 8

                val bitInsideByte =
                    bitIndex % 8

                val shift =
                    7 - bitInsideByte

                if (bit == 1) {
                    result[byteIndex] =
                        (
                                result[byteIndex].toInt() or
                                        (1 shl shift)
                                ).toByte()
                }

                bitIndex++
            }
        }

        check(bitIndex == INPUT_BYTES * 8)

        return result
    }
}