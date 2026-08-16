package com.kieran.optiframe.protocol

object Of8InnerFecErrorsErasuresDecoder {

    fun decode(
        interleaved336: ByteArray,
        erasurePositions: IntArray = intArrayOf()
    ): ByteArray {

        require(
            interleaved336.size ==
                    ProtocolConstants.OF8_RS_INTERLEAVED_BYTES
        ) {
            "OF8 inner FEC requires exactly " +
                    "${ProtocolConstants.OF8_RS_INTERLEAVED_BYTES} bytes"
        }

        require(
            erasurePositions.distinct().size ==
                    erasurePositions.size
        ) {
            "Erasure positions must be unique"
        }

        erasurePositions.forEach { position ->
            require(position in interleaved336.indices) {
                "Invalid OF8 interleaved erasure position: $position"
            }
        }

        val (
            firstCodeword,
            secondCodeword
        ) =
            ByteInterleaver.deinterleaveTwo(
                interleaved336
            )

        /*
         * Interleaving is:
         *
         * index 0 -> cw0[0]
         * index 1 -> cw1[0]
         * index 2 -> cw0[1]
         * index 3 -> cw1[1]
         * ...
         *
         * Therefore:
         *
         * even position -> first codeword
         * odd position  -> second codeword
         */
        val firstErasures =
            mutableListOf<Int>()

        val secondErasures =
            mutableListOf<Int>()

        for (position in erasurePositions) {

            val codewordPosition =
                position / 2

            if (position % 2 == 0) {
                firstErasures += codewordPosition
            } else {
                secondErasures += codewordPosition
            }
        }

        val firstData =
            Rs168136ErrorsErasuresDecoder.decode(
                received = firstCodeword,
                erasurePositions =
                    firstErasures.toIntArray()
            )

        val secondData =
            Rs168136ErrorsErasuresDecoder.decode(
                received = secondCodeword,
                erasurePositions =
                    secondErasures.toIntArray()
            )

        val result =
            ByteArray(
                ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
            )

        firstData.copyInto(
            destination = result,
            destinationOffset = 0
        )

        secondData.copyInto(
            destination = result,
            destinationOffset =
                ProtocolConstants.OF8_RS_DATA_BYTES
        )

        return result
    }
}