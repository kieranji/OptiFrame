package com.kieran.optiframe.protocol

object Of8InnerFecCleanDecoder {

    fun decode(
        interleaved336: ByteArray
    ): ByteArray {

        require(
            interleaved336.size ==
                    ProtocolConstants.OF8_RS_INTERLEAVED_BYTES
        ) {
            "OF8 inner FEC input must be exactly " +
                    "${ProtocolConstants.OF8_RS_INTERLEAVED_BYTES} bytes"
        }

        val (firstCodeword, secondCodeword) =
            ByteInterleaver.deinterleaveTwo(
                interleaved336
            )

        val firstData =
            Rs168136CleanDecoder.decode(
                firstCodeword
            )

        val secondData =
            Rs168136CleanDecoder.decode(
                secondCodeword
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