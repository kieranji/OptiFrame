package com.kieran.optiframe.protocol

object Of8InnerFecDecoder {

    fun decode(
        interleaved336: ByteArray
    ): ByteArray {

        require(
            interleaved336.size ==
                    ProtocolConstants.OF8_RS_INTERLEAVED_BYTES
        ) {
            "OF8 inner FEC requires exactly " +
                    "${ProtocolConstants.OF8_RS_INTERLEAVED_BYTES} bytes, " +
                    "but got ${interleaved336.size}"
        }

        val (
            firstCodeword,
            secondCodeword
        ) =
            ByteInterleaver.deinterleaveTwo(
                interleaved336
            )

        val firstData =
            Rs168136Decoder.decode(
                firstCodeword
            )

        val secondData =
            Rs168136Decoder.decode(
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