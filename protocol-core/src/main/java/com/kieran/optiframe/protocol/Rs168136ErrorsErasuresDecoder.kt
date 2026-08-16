package com.kieran.optiframe.protocol

object Rs168136ErrorsErasuresDecoder {

    const val DATA_BYTES = 136
    const val CODEWORD_BYTES = 168

    private const val SHORTENING_BYTES = 87

    fun decode(
        received: ByteArray,
        erasurePositions: IntArray = intArrayOf()
    ): ByteArray {

        require(received.size == CODEWORD_BYTES) {
            "RS(168,136) requires exactly $CODEWORD_BYTES bytes"
        }

        require(
            erasurePositions.distinct().size ==
                    erasurePositions.size
        ) {
            "Erasure positions must be unique"
        }

        erasurePositions.forEach { position ->
            require(position in 0 until CODEWORD_BYTES) {
                "Invalid shortened RS erasure position: $position"
            }
        }

        // Restore the 87 virtual leading zeros.
        val fullCodeword =
            ByteArray(
                Rs255223Encoder.CODEWORD_BYTES
            )

        received.copyInto(
            destination = fullCodeword,
            destinationOffset = SHORTENING_BYTES
        )

        // Positions in the shortened 168 B codeword
        // become positions + 87 in the 255 B mother codeword.
        val shiftedErasures =
            IntArray(erasurePositions.size) { index ->
                erasurePositions[index] +
                        SHORTENING_BYTES
            }

        val correctedFull =
            Rs255223ErrorsErasuresDecoder.decode(
                received = fullCodeword,
                erasurePositions = shiftedErasures
            )

        // Actual systematic data occupies mother-code
        // positions 87..222 inclusive.
        return correctedFull.copyOfRange(
            SHORTENING_BYTES,
            Rs255223Encoder.DATA_BYTES
        )
    }
}