package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Of8SpatialPermutationTest {

    @Test
    fun pc002KnownParametersMatch() {

        val params =
            Of8SpatialPermutation
                .deriveParameters(
                    frameSequence = 0x12345678L,
                    slotIndex = 0x05
                )

        assertEquals(
            229,
            params.a
        )

        assertEquals(
            555,
            params.b
        )

        assertEquals(
            493,
            params.aInverse
        )
    }

    @Test
    fun knownPermutationPositionsMatch() {

        val input =
            IntArray(
                ProtocolConstants.OF8_MACROBLOCK_CELLS
            ) {
                it
            }

        val result =
            Of8SpatialPermutation.permute(
                symbols = input,
                frameSequence = 0x12345678L,
                slotIndex = 0x05
            )

        assertEquals(
            0,
            result[555]
        )

        assertEquals(
            1,
            result[784]
        )

        assertEquals(
            2,
            result[117]
        )

        assertEquals(
            3,
            result[346]
        )

        assertEquals(
            4,
            result[575]
        )

        assertEquals(
            5,
            result[804]
        )
    }

    @Test
    fun permutationIsExactlyReversible() {

        val original =
            IntArray(
                ProtocolConstants.OF8_MACROBLOCK_CELLS
            ) {
                it % 8
            }

        val permuted =
            Of8SpatialPermutation.permute(
                symbols = original,
                frameSequence = 987654321L,
                slotIndex = 7
            )

        val recovered =
            Of8SpatialPermutation
                .inversePermute(
                    permuted = permuted,
                    frameSequence = 987654321L,
                    slotIndex = 7
                )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun matrixRoundTripRecovers336Bytes() {

        val original =
            ByteArray(
                Of8SymbolCodec.INPUT_BYTES
            ) {
                ((it * 73 + 41) and 0xFF)
                    .toByte()
            }

        val matrix =
            Of8MatrixCodec.encode(
                interleaved336 = original,
                frameSequence = 12345,
                slotIndex = 3
            )

        assertEquals(
            32,
            matrix.width
        )

        assertEquals(
            28,
            matrix.height
        )

        assertEquals(
            896,
            matrix.cells.size
        )

        val recovered =
            Of8MatrixCodec.decode(
                matrix = matrix,
                frameSequence = 12345,
                slotIndex = 3
            )

        assertContentEquals(
            original,
            recovered
        )
    }
}