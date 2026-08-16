package com.kieran.optiframe.protocol

object Of8InnerFecEncoder {

    fun encode(
        whitenedObject: ByteArray
    ): ByteArray {
        require(
            whitenedObject.size ==
                    ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
        ) {
            "OF8 object must be exactly " +
                    "${ProtocolConstants.OF8_PLAIN_OBJECT_BYTES} bytes"
        }

        val firstData =
            whitenedObject.copyOfRange(
                0,
                ProtocolConstants.OF8_RS_DATA_BYTES
            )

        val secondData =
            whitenedObject.copyOfRange(
                ProtocolConstants.OF8_RS_DATA_BYTES,
                ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
            )

        val firstCodeword =
            Rs168136Encoder.encode(firstData)

        val secondCodeword =
            Rs168136Encoder.encode(secondData)

        val interleaved =
            ByteInterleaver.interleaveTwo(
                firstCodeword,
                secondCodeword
            )

        check(
            interleaved.size ==
                    ProtocolConstants.OF8_RS_INTERLEAVED_BYTES
        ) {
            "Unexpected OF8 inner-FEC length: " +
                    interleaved.size
        }

        return interleaved
    }
}