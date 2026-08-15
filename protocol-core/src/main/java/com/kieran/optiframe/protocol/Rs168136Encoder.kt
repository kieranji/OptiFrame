package com.kieran.optiframe.protocol

object Rs168136Encoder {

    const val DATA_BYTES = 136
    const val CODEWORD_BYTES = 168

    private const val SHORTENING_BYTES = 87

    fun encode(data: ByteArray): ByteArray {
        require(data.size == DATA_BYTES) {
            "RS(168,136) requires exactly $DATA_BYTES data bytes, " +
                    "but got ${data.size}"
        }

        // RS(255,223) mother-code input:
        //
        // 87 virtual zero bytes
        // + 136 actual data bytes
        // = 223 bytes
        val padded =
            ByteArray(
                Rs255223Encoder.DATA_BYTES
            )

        data.copyInto(
            destination = padded,
            destinationOffset = SHORTENING_BYTES
        )

        val fullCodeword =
            Rs255223Encoder.encode(padded)

        // Do not transmit the 87 known virtual zeros.
        return fullCodeword.copyOfRange(
            SHORTENING_BYTES,
            fullCodeword.size
        )
    }
}