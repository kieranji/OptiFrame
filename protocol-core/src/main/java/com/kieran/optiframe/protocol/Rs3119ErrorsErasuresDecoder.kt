package com.kieran.optiframe.protocol

object Rs3119ErrorsErasuresDecoder {

    const val DATA_BYTES = 19
    const val CODEWORD_BYTES = 31

    private const val SHORTENING_BYTES = 224

    fun decode(
        received: ByteArray,
        erasurePositions: IntArray =
            intArrayOf()
    ): ByteArray {

        require(
            received.size ==
                    CODEWORD_BYTES
        ) {
            "RS(31,19) requires exactly 31 bytes"
        }

        require(
            erasurePositions.distinct().size ==
                    erasurePositions.size
        )

        erasurePositions.forEach { position ->

            require(
                position in 0 until CODEWORD_BYTES
            ) {
                "Invalid RS(31,19) erasure position: $position"
            }
        }

        val full =
            ByteArray(
                Rs255243Encoder.CODEWORD_BYTES
            )

        received.copyInto(
            destination = full,
            destinationOffset =
                SHORTENING_BYTES
        )

        val shiftedErasures =
            IntArray(
                erasurePositions.size
            ) { index ->

                erasurePositions[index] +
                        SHORTENING_BYTES
            }

        val corrected =
            Rs255243ErrorsErasuresDecoder
                .decode(
                    received = full,
                    erasurePositions =
                        shiftedErasures
                )

        return corrected.copyOfRange(
            SHORTENING_BYTES,
            Rs255243Encoder.DATA_BYTES
        )
    }
}