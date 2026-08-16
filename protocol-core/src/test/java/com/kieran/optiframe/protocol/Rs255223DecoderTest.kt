package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals

class Rs255223DecoderTest {

    @Test
    fun cleanCodewordPassesUnchanged() {

        val data =
            createTestData()

        val encoded =
            Rs255223Encoder.encode(
                data
            )

        val decoded =
            Rs255223Decoder.decode(
                encoded
            )

        assertContentEquals(
            encoded,
            decoded
        )
    }

    @Test
    fun singleByteErrorIsCorrected() {

        val original =
            Rs255223Encoder.encode(
                createTestData()
            )

        val damaged =
            original.copyOf()

        damaged[100] =
            (
                    damaged[100].toInt() xor
                            0x5A
                    ).toByte()

        val recovered =
            Rs255223Decoder.decode(
                damaged
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun eightErrorsAreCorrected() {

        val original =
            Rs255223Encoder.encode(
                createTestData()
            )

        val damaged =
            original.copyOf()

        val positions =
            intArrayOf(
                5,
                31,
                62,
                93,
                124,
                155,
                200,
                249
            )

        for (
        (index, position) in
        positions.withIndex()
        ) {

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                (0x11 + index)
                        ).toByte()
        }

        val recovered =
            Rs255223Decoder.decode(
                damaged
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun sixteenErrorsAreCorrected() {

        val original =
            Rs255223Encoder.encode(
                createTestData()
            )

        val damaged =
            original.copyOf()

        val positions =
            intArrayOf(
                1, 14, 27, 40,
                53, 66, 79, 92,
                105, 118, 131, 144,
                157, 180, 220, 250
            )

        for (
        (index, position) in
        positions.withIndex()
        ) {

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                ((index + 1) * 7)
                        ).toByte()
        }

        val recovered =
            Rs255223Decoder.decode(
                damaged
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    private fun createTestData(): ByteArray {

        return ByteArray(
            Rs255223Encoder.DATA_BYTES
        ) {
            ((it * 73 + 19) and 0xFF)
                .toByte()
        }
    }
}