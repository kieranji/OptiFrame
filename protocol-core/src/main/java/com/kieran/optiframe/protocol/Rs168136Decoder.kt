package com.kieran.optiframe.protocol

object Rs168136Decoder {

    const val DATA_BYTES = 136
    const val CODEWORD_BYTES = 168

    private const val SHORTENING_BYTES = 87

    fun decode(
        received: ByteArray
    ): ByteArray {

        require(
            received.size == CODEWORD_BYTES
        ) {
            "RS(168,136) requires exactly " +
                    "$CODEWORD_BYTES bytes"
        }

        /*
         * Restore the 87 virtual leading zero bytes:
         *
         * 87 virtual zeros
         * + 136 transmitted systematic data
         * + 32 parity
         * = 255 bytes
         */
        val fullCodeword =
            ByteArray(
                Rs255223Encoder.CODEWORD_BYTES
            )

        received.copyInto(
            destination = fullCodeword,
            destinationOffset = SHORTENING_BYTES
        )

        val correctedFull =
            Rs255223Decoder.decode(
                fullCodeword
            )

        /*
         * The mother code is systematic:
         *
         * 0..86    = virtual zeros
         * 87..222  = actual 136 B data
         * 223..254 = parity
         */
        return correctedFull.copyOfRange(
            SHORTENING_BYTES,
            Rs255223Encoder.DATA_BYTES
        )
    }
}