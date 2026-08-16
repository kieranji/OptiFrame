package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class Rs255223ErasureDecoderTest {

    @Test
    fun cleanCodewordPasses() {

        val original =
            createCodeword()

        val recovered =
            Rs255223ErasureDecoder.decode(
                received = original,
                erasurePositions = intArrayOf()
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun singleErasureIsRecovered() {

        val original =
            createCodeword()

        val damaged =
            original.copyOf()

        val position = 100

        damaged[position] =
            (
                    damaged[position].toInt() xor
                            0x7A
                    ).toByte()

        val recovered =
            Rs255223ErasureDecoder.decode(
                received = damaged,
                erasurePositions =
                    intArrayOf(position)
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun sixteenErasuresAreRecovered() {

        val original =
            createCodeword()

        val damaged =
            original.copyOf()

        val positions =
            intArrayOf(
                1, 13, 27, 41,
                55, 69, 83, 97,
                111, 125, 139, 153,
                177, 201, 229, 250
            )

        positions.forEachIndexed { index, position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                ((index + 1) * 7)
                        ).toByte()
        }

        val recovered =
            Rs255223ErasureDecoder.decode(
                received = damaged,
                erasurePositions = positions
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun thirtyTwoErasuresAreRecovered() {

        val original =
            createCodeword()

        val damaged =
            original.copyOf()

        val positions =
            IntArray(32) { index ->
                index * 7
            }

        positions.forEachIndexed { index, position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                ((index + 1) and 0xFF)
                        ).toByte()
        }

        val recovered =
            Rs255223ErasureDecoder.decode(
                received = damaged,
                erasurePositions = positions
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun unmarkedUnknownErrorIsRejected() {

        val original =
            createCodeword()

        val damaged =
            original.copyOf()

        val knownErasure = 50

        damaged[knownErasure] =
            (
                    damaged[knownErasure].toInt() xor
                            0x44
                    ).toByte()

        // Additional unknown error that is NOT marked as erasure.
        damaged[120] =
            (
                    damaged[120].toInt() xor
                            0x77
                    ).toByte()

        assertFailsWith<IllegalArgumentException> {

            Rs255223ErasureDecoder.decode(
                received = damaged,
                erasurePositions =
                    intArrayOf(knownErasure)
            )
        }
    }

    private fun createCodeword(): ByteArray {

        val data =
            ByteArray(
                Rs255223Encoder.DATA_BYTES
            ) {
                ((it * 73 + 19) and 0xFF)
                    .toByte()
            }

        return Rs255223Encoder.encode(
            data
        )
    }
}