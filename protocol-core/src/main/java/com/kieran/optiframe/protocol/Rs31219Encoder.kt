package com.kieran.optiframe.protocol

object Rs31219Encoder {

    const val DATA_BYTES = 19
    const val CODEWORD_BYTES = 31

    private const val SHORTENING_BYTES = 224

    fun encode(
        data: ByteArray
    ): ByteArray {

        require(data.size == DATA_BYTES) {
            "RS(31,19) requires exactly 19 bytes"
        }

        val padded =
            ByteArray(
                Rs255243Encoder.DATA_BYTES
            )

        data.copyInto(
            destination = padded,
            destinationOffset =
                SHORTENING_BYTES
        )

        val full =
            Rs255243Encoder.encode(
                padded
            )

        return full.copyOfRange(
            SHORTENING_BYTES,
            full.size
        )
    }

    fun cleanDecode(
        received: ByteArray
    ): ByteArray {

        require(
            received.size == CODEWORD_BYTES
        )

        val full =
            ByteArray(
                Rs255243Encoder.CODEWORD_BYTES
            )

        received.copyInto(
            destination = full,
            destinationOffset =
                SHORTENING_BYTES
        )

        val syndromes =
            Rs255243Encoder.syndromes(
                full
            )

        require(
            syndromes.all { it == 0 }
        ) {
            "RS(31,19) codeword contains errors"
        }

        return received.copyOfRange(
            0,
            DATA_BYTES
        )
    }
}