package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GlobalHeaderMatrixCodecTest {

    @Test
    fun protectedHeaderMapsTo16By16Matrix() {

        val protected =
            ByteArray(31) {
                it.toByte()
            }

        val matrix =
            GlobalHeaderMatrixCodec.encode(
                protected
            )

        assertEquals(
            16,
            matrix.width
        )

        assertEquals(
            16,
            matrix.height
        )

        assertEquals(
            256,
            matrix.cells.size
        )
    }

    @Test
    fun matrixRoundTripPreserves31Bytes() {

        val original =
            ByteArray(31) {
                ((it * 73 + 19) and 0xFF)
                    .toByte()
            }

        val matrix =
            GlobalHeaderMatrixCodec.encode(
                original
            )

        val recovered =
            GlobalHeaderMatrixCodec.decode(
                matrix
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun mappingIsMsbFirst() {

        val input =
            ByteArray(31)

        input[0] =
            0b10110010.toByte()

        val matrix =
            GlobalHeaderMatrixCodec.encode(
                input
            )

        assertContentEquals(
            intArrayOf(
                1, 0, 1, 1,
                0, 0, 1, 0
            ),
            matrix.cells.copyOfRange(
                0,
                8
            )
        )
    }

    @Test
    fun fixedTrailerMatchesPc006() {

        val matrix =
            GlobalHeaderMatrixCodec.encode(
                ByteArray(31)
            )

        assertContentEquals(
            intArrayOf(
                1, 0, 1, 0,
                1, 0, 1, 0
            ),
            matrix.cells.copyOfRange(
                248,
                256
            )
        )
    }

    @Test
    fun damagedFixedTrailerIsRejected() {

        val matrix =
            GlobalHeaderMatrixCodec.encode(
                ByteArray(31)
            )

        val damaged =
            matrix.cells.copyOf()

        damaged[250] =
            damaged[250] xor 1

        assertFailsWith<IllegalArgumentException> {

            GlobalHeaderMatrixCodec.decode(
                GlobalHeaderLogicalMatrix(
                    damaged
                )
            )
        }
    }

    @Test
    fun completeHeaderMatrixRoundTripWorks() {

        val original =
            createHeader()

        val matrix =
            OptiFrameGlobalHeaderMatrixCodec
                .encode(original)

        val recovered =
            OptiFrameGlobalHeaderMatrixCodec
                .decode(matrix)

        assertEquals(
            original,
            recovered
        )
    }

    private fun createHeader():
            OptiFrameGlobalHeader {

        return OptiFrameGlobalHeader(
            profileId =
                ProtocolConstants.PROFILE_P1_ID,

            sessionId =
                0x12345678L,

            frameSequence =
                0x9ABCDEF0L,

            sourceBlockNumber =
                42,

            paletteId =
                ProtocolConstants.PALETTE_OF8_FIXED,

            frameType =
                ProtocolConstants.FRAME_TYPE_DATA,

            flags =
                0,

            calibrationEpoch =
                3
        )
    }
}