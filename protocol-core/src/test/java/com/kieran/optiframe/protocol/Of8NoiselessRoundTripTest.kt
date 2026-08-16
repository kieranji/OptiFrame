package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Of8NoiselessRoundTripTest {

    @Test
    fun completeOf8OpticalMatrixRoundTripWorks() {

        val sessionId =
            0x12345678L

        val frameSequence =
            0x9ABC5678L

        val slotIndex =
            3

        val original =
            "OptiFrame V1 complete optical matrix round-trip"
                .encodeToByteArray()

        // ---------------------------------------------------------
        // Build 256 B source symbol
        // ---------------------------------------------------------

        val payload =
            ByteArray(
                ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
            )

        original.copyInto(payload)

        // ---------------------------------------------------------
        // Build OF8 macroblock
        // ---------------------------------------------------------

        val block =
            Of8Macroblock(
                header = Of8MacroblockHeader(
                    esi = 1234,
                    slotIndex = slotIndex,
                    flags = 0,
                    frameSeqLow =
                        (frameSequence and 0xFFFF)
                            .toInt()
                ),
                payload = payload
            )

        // ---------------------------------------------------------
        // ENCODE
        // ---------------------------------------------------------

        // header + payload + CRC32C
        val object272 =
            Of8MacroblockCodec.encode(
                block
            )

        assertEquals(
            272,
            object272.size
        )

        // Whitening
        val whitened272 =
            Whitening.apply(
                data = object272,
                sessionId = sessionId,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        // 2 × shortened RS(168,136)
        // + byte interleave
        val interleaved336 =
            Of8InnerFecEncoder.encode(
                whitened272
            )

        assertEquals(
            336,
            interleaved336.size
        )

        // MSB-first 3-bit symbols
        // + spatial permutation
        // + 32×28 logical matrix
        val matrix =
            Of8MatrixCodec.encode(
                interleaved336 = interleaved336,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        assertEquals(
            896,
            matrix.cells.size
        )

        // Logical symbols → ideal OF8 RGB colors
        val colors =
            Of8Palette.matrixToColors(
                matrix
            )

        assertEquals(
            896,
            colors.size
        )

        // =========================================================
        // DECODE
        // =========================================================

        // Ideal RGB → logical symbols
        val receivedMatrix =
            Of8Palette.colorsToMatrix(
                colors
            )

        // inverse permutation
        // + 3-bit symbols → 336 bytes
        val received336 =
            Of8MatrixCodec.decode(
                matrix = receivedMatrix,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        assertContentEquals(
            interleaved336,
            received336
        )

        // Deinterleave + clean shortened RS decode
        val receivedWhitened272 =
            Of8InnerFecCleanDecoder.decode(
                received336
            )

        assertContentEquals(
            whitened272,
            receivedWhitened272
        )

        // XOR whitening is its own inverse
        val recovered272 =
            Whitening.apply(
                data = receivedWhitened272,
                sessionId = sessionId,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        assertContentEquals(
            object272,
            recovered272
        )

        // CRC32C + header validation + payload extraction
        val recoveredBlock =
            Of8MacroblockCodec.decode(
                recovered272
            )

        assertEquals(
            block.header,
            recoveredBlock.header
        )

        assertContentEquals(
            block.payload,
            recoveredBlock.payload
        )

        // Remove zero padding
        val recoveredOriginal =
            recoveredBlock.payload
                .copyOf(original.size)

        assertContentEquals(
            original,
            recoveredOriginal
        )

        // Final bit-perfect verification
        assertEquals(
            Sha256.hex(original),
            Sha256.hex(recoveredOriginal)
        )
    }
}