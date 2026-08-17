package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Of8ConfidenceMapperTest {

    @Test
    fun perfectCellsProducePerfectBytes() {

        val cellConfidence =
            DoubleArray(
                Of8ConfidenceMapper.CELL_COUNT
            ) {
                1.0
            }

        val byteConfidence =
            Of8ConfidenceMapper
                .cellConfidenceToByteConfidence(
                    cellConfidence
                )

        assertEquals(
            336,
            byteConfidence.size
        )

        byteConfidence.forEach {
            assertEquals(
                1.0,
                it
            )
        }
    }

    @Test
    fun lowConfidenceCellAffectsEveryByteUsingItsBits() {

        val cellConfidence =
            DoubleArray(
                Of8ConfidenceMapper.CELL_COUNT
            ) {
                1.0
            }

        /*
         * Cell 2 contains bit indexes:
         *
         * 6, 7, 8
         *
         * Bits 6 and 7 belong to byte 0.
         * Bit 8 belongs to byte 1.
         *
         * Therefore both byte 0 and byte 1 must inherit
         * this cell's low confidence.
         */
        cellConfidence[2] =
            0.20

        val byteConfidence =
            Of8ConfidenceMapper
                .cellConfidenceToByteConfidence(
                    cellConfidence
                )

        assertEquals(
            0.20,
            byteConfidence[0]
        )

        assertEquals(
            0.20,
            byteConfidence[1]
        )

        assertEquals(
            1.0,
            byteConfidence[2]
        )
    }

    @Test
    fun thresholdCreatesCorrectErasurePositions() {

        val byteConfidence =
            DoubleArray(
                Of8ConfidenceMapper.BYTE_COUNT
            ) {
                0.95
            }

        byteConfidence[5] =
            0.30

        byteConfidence[100] =
            0.49

        byteConfidence[335] =
            0.10

        val erasures =
            Of8ConfidenceMapper
                .erasurePositions(
                    byteConfidence =
                        byteConfidence,
                    threshold = 0.50
                )

        assertContentEquals(
            intArrayOf(
                5,
                100,
                335
            ),
            erasures
        )
    }

    @Test
    fun confidenceSpatialPermutationIsReversible() {

        val original =
            DoubleArray(
                ProtocolConstants.OF8_MACROBLOCK_CELLS
            ) {
                it.toDouble() / 895.0
            }

        /*
         * Create the spatially permuted form manually using
         * the same permutation parameters.
         */
        val params =
            Of8SpatialPermutation
                .deriveParameters(
                    frameSequence = 123456,
                    slotIndex = 4
                )

        val permuted =
            DoubleArray(original.size)

        for (i in original.indices) {

            val j =
                (
                        params.a.toLong() * i +
                                params.b
                        ).mod(original.size.toLong())
                    .toInt()

            permuted[j] =
                original[i]
        }

        val recovered =
            Of8SpatialPermutation
                .inversePermuteConfidence(
                    permuted = permuted,
                    frameSequence = 123456,
                    slotIndex = 4
                )

        assertContentEquals(
            original,
            recovered
        )
    }
}