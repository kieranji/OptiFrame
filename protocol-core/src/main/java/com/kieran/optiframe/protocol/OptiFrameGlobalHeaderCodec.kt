package com.kieran.optiframe.protocol

object OptiFrameGlobalHeaderCodec {

    fun encode(
        header: OptiFrameGlobalHeader
    ): ByteArray {

        val output =
            ByteArray(
                ProtocolConstants.GLOBAL_HEADER_DATA_BYTES
            )

        var offset = 0

        ProtocolConstants.FRAME_MAGIC.copyInto(
            output,
            destinationOffset = offset
        )
        offset += 2

        output[offset++] =
            ProtocolConstants.PROTOCOL_VERSION.toByte()

        output[offset++] =
            header.profileId.toByte()

        BinaryIo.writeUInt32BE(
            header.sessionId
        ).copyInto(output, offset)

        offset += 4

        BinaryIo.writeUInt32BE(
            header.frameSequence
        ).copyInto(output, offset)

        offset += 4

        BinaryIo.writeUInt16BE(
            header.sourceBlockNumber
        ).copyInto(output, offset)

        offset += 2

        output[offset++] =
            header.paletteId.toByte()

        output[offset++] =
            header.frameType.toByte()

        output[offset++] =
            header.flags.toByte()

        output[offset++] =
            header.calibrationEpoch.toByte()

        check(
            offset ==
                    ProtocolConstants.GLOBAL_HEADER_PRE_CRC_BYTES
        )

        output[offset] =
            Crc8Atm.compute(
                output,
                offset = 0,
                length =
                    ProtocolConstants.GLOBAL_HEADER_PRE_CRC_BYTES
            ).toByte()

        return output
    }

    fun decode(
        data: ByteArray
    ): OptiFrameGlobalHeader {

        require(
            data.size ==
                    ProtocolConstants.GLOBAL_HEADER_DATA_BYTES
        ) {
            "Global header must contain exactly 19 bytes"
        }

        require(
            data[0] == 0x4F.toByte() &&
                    data[1] == 0x46.toByte()
        ) {
            "Invalid global header magic"
        }

        val version =
            data[2].toInt() and 0xFF

        require(
            version ==
                    ProtocolConstants.PROTOCOL_VERSION
        ) {
            "Unsupported protocol version: $version"
        }

        val expectedCrc =
            data[18].toInt() and 0xFF

        val actualCrc =
            Crc8Atm.compute(
                data,
                offset = 0,
                length =
                    ProtocolConstants.GLOBAL_HEADER_PRE_CRC_BYTES
            )

        require(
            expectedCrc == actualCrc
        ) {
            "Global header CRC-8 mismatch"
        }

        return OptiFrameGlobalHeader(
            profileId =
                data[3].toInt() and 0xFF,

            sessionId =
                BinaryIo.readUInt32BE(
                    data,
                    4
                ),

            frameSequence =
                BinaryIo.readUInt32BE(
                    data,
                    8
                ),

            sourceBlockNumber =
                BinaryIo.readUInt16BE(
                    data,
                    12
                ),

            paletteId =
                data[14].toInt() and 0xFF,

            frameType =
                data[15].toInt() and 0xFF,

            flags =
                data[16].toInt() and 0xFF,

            calibrationEpoch =
                data[17].toInt() and 0xFF
        )
    }
}