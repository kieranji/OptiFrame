package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals

class Rs168136DecoderTest {

    @Test
    fun cleanShortenedCodewordDecodes() {

        val original =
            createData()

        val encoded =
            Rs168136Encoder.encode(
                original
            )

        val decoded =
            Rs168136Decoder.decode(
                encoded
            )

        assertContentEquals(
            original,
            decoded
        )
    }

    @Test
    fun singleErrorIsCorrected() {

        val original =
            createData()

        val damaged =
            Rs168136Encoder
                .encode(original)
                .copyOf()

        damaged[73] =
            (
                    damaged[73].toInt() xor
                            0x5A
                    ).toByte()

        val decoded =
            Rs168136Decoder.decode(
                damaged
            )

        assertContentEquals(
            original,
            decoded
        )
    }

    @Test
    fun eightErrorsAreCorrected() {

        val original =
            createData()

        val damaged =
            Rs168136Encoder
                .encode(original)
                .copyOf()

        val positions =
            intArrayOf(
                3,
                21,
                42,
                67,
                91,
                119,
                145,
                165
            )

        positions.forEachIndexed { index, position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                (0x21 + index)
                        ).toByte()
        }

        val decoded =
            Rs168136Decoder.decode(
                damaged
            )

        assertContentEquals(
            original,
            decoded
        )
    }

    @Test
    fun sixteenErrorsAreCorrected() {

        val original =
            createData()

        val damaged =
            Rs168136Encoder
                .encode(original)
                .copyOf()

        val positions =
            intArrayOf(
                0, 10, 20, 30,
                40, 50, 60, 70,
                80, 90, 100, 110,
                120, 135, 150, 167
            )

        positions.forEachIndexed { index, position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                ((index + 1) * 5)
                        ).toByte()
        }

        val decoded =
            Rs168136Decoder.decode(
                damaged
            )

        assertContentEquals(
            original,
            decoded
        )
    }

    private fun createData(): ByteArray {

        return ByteArray(
            Rs168136Encoder.DATA_BYTES
        ) {
            ((it * 73 + 31) and 0xFF)
                .toByte()
        }
    }
}