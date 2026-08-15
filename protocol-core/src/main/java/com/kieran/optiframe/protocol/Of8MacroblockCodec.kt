package com.kieran.optiframe.protocol

object Of8MacroblockCodec {

    fun encode(block: Of8Macroblock): ByteArray {
        require(
            block.header.symbolLength ==
                    ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
        ) {
            "V1 OF8 symbolLength must be " +
                    ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
        }

        require(block.header.reserved == 0) {
            "V1 reserved field must be 0"
        }

        val plain = ByteArray(
            ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
        )

        var offset = 0

        BinaryIo.writeUInt32BE(block.header.esi)
            .copyInto(plain, offset)
        offset += 4

        plain[offset++] =
            block.header.slotIndex.toByte()

        plain[offset++] =
            block.header.flags.toByte()

        BinaryIo.writeUInt16BE(block.header.frameSeqLow)
            .copyInto(plain, offset)
        offset += 2

        BinaryIo.writeUInt16BE(block.header.symbolLength)
            .copyInto(plain, offset)
        offset += 2

        BinaryIo.writeUInt16BE(block.header.reserved)
            .copyInto(plain, offset)
        offset += 2

        block.payload.copyInto(
            destination = plain,
            destinationOffset = offset
        )
        offset += block.payload.size

        check(offset == 268) {
            "Unexpected OF8 pre-CRC length: $offset"
        }

        val crc = Crc32c.compute(
            data = plain,
            offset = 0,
            length = 268
        )

        BinaryIo.writeUInt32BE(crc)
            .copyInto(plain, 268)

        return plain
    }

    fun decode(encoded: ByteArray): Of8Macroblock {
        require(
            encoded.size ==
                    ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
        ) {
            "OF8 macroblock must be exactly " +
                    "${ProtocolConstants.OF8_PLAIN_OBJECT_BYTES} bytes"
        }

        val expectedCrc =
            BinaryIo.readUInt32BE(encoded, 268)

        val actualCrc =
            Crc32c.compute(
                data = encoded,
                offset = 0,
                length = 268
            )

        require(expectedCrc == actualCrc) {
            "CRC32C mismatch: expected " +
                    "0x${expectedCrc.toString(16)}, actual " +
                    "0x${actualCrc.toString(16)}"
        }

        val esi =
            BinaryIo.readUInt32BE(encoded, 0)

        val slotIndex =
            encoded[4].toInt() and 0xFF

        val flags =
            encoded[5].toInt() and 0xFF

        val frameSeqLow =
            BinaryIo.readUInt16BE(encoded, 6)

        val symbolLength =
            BinaryIo.readUInt16BE(encoded, 8)

        val reserved =
            BinaryIo.readUInt16BE(encoded, 10)

        require(
            symbolLength ==
                    ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
        ) {
            "Unsupported OF8 symbolLength: $symbolLength"
        }

        require(reserved == 0) {
            "V1 reserved field must be 0"
        }

        val payload =
            encoded.copyOfRange(
                12,
                12 + ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
            )

        return Of8Macroblock(
            header = Of8MacroblockHeader(
                esi = esi,
                slotIndex = slotIndex,
                flags = flags,
                frameSeqLow = frameSeqLow,
                symbolLength = symbolLength,
                reserved = reserved
            ),
            payload = payload
        )
    }
}