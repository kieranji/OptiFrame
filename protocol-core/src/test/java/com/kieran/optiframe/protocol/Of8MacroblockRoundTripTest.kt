package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Of8MacroblockRoundTripTest {

    @Test
    fun macroblockRoundTripPreservesPayload() {
        val original =
            "OptiFrame V1 round-trip test"
                .encodeToByteArray()

        val payload =
            ByteArray(
                ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
            )

        original.copyInto(payload)

        val block =
            Of8Macroblock(
                header = Of8MacroblockHeader(
                    esi = 1234,
                    slotIndex = 3,
                    flags = 0,
                    frameSeqLow = 0x5678
                ),
                payload = payload
            )

        val encoded =
            Of8MacroblockCodec.encode(block)

        assertEquals(
            ProtocolConstants.OF8_PLAIN_OBJECT_BYTES,
            encoded.size
        )

        val decoded =
            Of8MacroblockCodec.decode(encoded)

        assertEquals(
            block.header,
            decoded.header
        )

        assertContentEquals(
            block.payload,
            decoded.payload
        )

        val recovered =
            decoded.payload.copyOf(original.size)

        assertContentEquals(
            original,
            recovered
        )

        assertEquals(
            Sha256.hex(original),
            Sha256.hex(recovered)
        )
    }

    @Test
    fun corruptedMacroblockIsRejectedByCrc32c() {
        val payload =
            ByteArray(
                ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
            ) {
                it.toByte()
            }

        val block =
            Of8Macroblock(
                header = Of8MacroblockHeader(
                    esi = 42,
                    slotIndex = 1,
                    flags = 0,
                    frameSeqLow = 100
                ),
                payload = payload
            )

        val encoded =
            Of8MacroblockCodec.encode(block)

        encoded[100] =
            (encoded[100].toInt() xor 0x01)
                .toByte()

        assertFailsWith<IllegalArgumentException> {
            Of8MacroblockCodec.decode(encoded)
        }
    }
}