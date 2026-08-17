package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Of8DamagedMatrixRoundTripTest {

    @Test
    fun damagedOpticalMatrixRecoversOriginalPayload() {

        val sessionId =
            0x12345678L

        val frameSequence =
            0x87654321L

        val slotIndex =
            4

        val original =
            "OptiFrame V1 damaged optical matrix recovery test"
                .encodeToByteArray()

        // =========================================================
        // Build original payload
        // =========================================================

        val payload =
            ByteArray(
                ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
            )

        original.copyInto(payload)

        val block =
            Of8Macroblock(
                header = Of8MacroblockHeader(
                    esi = 100,
                    slotIndex = slotIndex,
                    flags = 0,
                    frameSeqLow =
                        (frameSequence and 0xFFFF)
                            .toInt()
                ),
                payload = payload
            )

        // =========================================================
        // ENCODE
        // =========================================================

        val object272 =
            Of8MacroblockCodec.encode(
                block
            )

        val whitened272 =
            Whitening.apply(
                data = object272,
                sessionId = sessionId,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        val interleaved336 =
            Of8InnerFecEncoder.encode(
                whitened272
            )

        val matrix =
            Of8MatrixCodec.encode(
                interleaved336 = interleaved336,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        val colors =
            Of8Palette
                .matrixToColors(matrix)
                .copyOf()

        /*
         * At this point:
         *
         * colors[] = what the sender would ideally display.
         *
         * We now simulate optical corruption.
         */

        val confidence =
            DoubleArray(
                ProtocolConstants.OF8_MACROBLOCK_CELLS
            ) {
                1.0
            }

        val params =
            Of8SpatialPermutation
                .deriveParameters(
                    frameSequence = frameSequence,
                    slotIndex = slotIndex
                )

        // =========================================================
        // KNOWN ERASURES
        // =========================================================
        //
        // These cells are decoded incorrectly,
        // BUT receiver confidence is low.
        //
        // Therefore affected byte(s) should be marked erasure.
        // =========================================================

        val lowConfidenceCanonicalCells =
            intArrayOf(
                2,
                51,
                173,
                401
            )

        for (
        canonicalCell in
        lowConfidenceCanonicalCells
        ) {

            val spatialCell =
                spatialIndex(
                    canonicalIndex = canonicalCell,
                    a = params.a,
                    b = params.b
                )

            corruptCell(
                colors = colors,
                spatialIndex = spatialCell,
                currentSymbol =
                    matrix.cells[spatialCell]
            )

            confidence[spatialCell] =
                0.10
        }

        // =========================================================
        // UNKNOWN ERRORS
        // =========================================================
        //
        // These cells are wrong, but confidence remains high.
        //
        // RS therefore sees them as unknown errors.
        // =========================================================

        val unknownErrorCanonicalCells =
            intArrayOf(
                100,
                250,
                600
            )

        for (
        canonicalCell in
        unknownErrorCanonicalCells
        ) {

            val spatialCell =
                spatialIndex(
                    canonicalIndex = canonicalCell,
                    a = params.a,
                    b = params.b
                )

            corruptCell(
                colors = colors,
                spatialIndex = spatialCell,
                currentSymbol =
                    matrix.cells[spatialCell]
            )

            // Intentionally leave:
            //
            // confidence = 1.0
        }

        // =========================================================
        // RECEIVER
        // =========================================================

        val receivedMatrix =
            Of8Palette.colorsToMatrix(
                colors
            )

        val observation =
            Of8ObservedMatrixDecoder.decode(
                matrix = receivedMatrix,
                spatialCellConfidence = confidence,
                frameSequence = frameSequence,
                slotIndex = slotIndex,
                erasureThreshold = 0.50
            )

        /*
         * Because some cells were deliberately given
         * low confidence, at least one byte must be erased.
         */

        assertTrue(
            observation.erasurePositions.isNotEmpty()
        )

        /*
         * The raw 336 B stream is damaged.
         *
         * If this assertion ever fails, our simulated corruption
         * is no longer actually introducing an error.
         */

        assertTrue(
            !interleaved336.contentEquals(
                observation.interleaved336
            )
        )

        // =========================================================
        // RS ERRORS + ERASURES
        // =========================================================

        val recoveredWhitened272 =
            Of8InnerFecErrorsErasuresDecoder.decode(
                interleaved336 =
                    observation.interleaved336,
                erasurePositions =
                    observation.erasurePositions
            )

        assertContentEquals(
            whitened272,
            recoveredWhitened272
        )

        // =========================================================
        // UNWHITEN
        // =========================================================

        val recovered272 =
            Whitening.apply(
                data = recoveredWhitened272,
                sessionId = sessionId,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        assertContentEquals(
            object272,
            recovered272
        )

        // =========================================================
        // CRC + MACROBLOCK
        // =========================================================

        val recoveredBlock =
            Of8MacroblockCodec.decode(
                recovered272
            )

        assertContentEquals(
            payload,
            recoveredBlock.payload
        )

        assertEquals(
            block.header,
            recoveredBlock.header
        )

        // =========================================================
        // ORIGINAL FILE BYTES
        // =========================================================

        val recoveredOriginal =
            recoveredBlock.payload
                .copyOf(original.size)

        assertContentEquals(
            original,
            recoveredOriginal
        )

        assertEquals(
            Sha256.hex(original),
            Sha256.hex(recoveredOriginal)
        )
    }

    private fun spatialIndex(
        canonicalIndex: Int,
        a: Int,
        b: Int
    ): Int {

        return (
                a.toLong() *
                        canonicalIndex +
                        b
                ).mod(
                ProtocolConstants
                    .OF8_MACROBLOCK_CELLS
                    .toLong()
            )
            .toInt()
    }

    private fun corruptCell(
        colors: Array<RgbColor>,
        spatialIndex: Int,
        currentSymbol: Int
    ) {

        /*
         * XOR with 001 changes exactly one of the
         * three transmitted symbol bits while keeping
         * the result inside 0..7.
         */

        val corruptedSymbol =
            currentSymbol xor 0x01

        colors[spatialIndex] =
            Of8Palette.symbolToColor(
                corruptedSymbol
            )
    }
}