package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals

class Rs3119ErrorsErasuresDecoderTest {

    @Test
    fun cleanCodewordDecodes() {

        val original =
            createData()

        val encoded =
            Rs31219Encoder.encode(
                original
            )

        val recovered =
            Rs3119ErrorsErasuresDecoder.decode(
                encoded
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun sixUnknownErrorsAreCorrected() {

        val original =
            createData()

        val damaged =
            Rs31219Encoder
                .encode(original)
                .copyOf()

        val positions =
            intArrayOf(
                0,
                5,
                10,
                15,
                22,
                30
            )

        positions.forEachIndexed {
                index,
                position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                ((index + 1) * 17)
                        ).toByte()
        }

        val recovered =
            Rs3119ErrorsErasuresDecoder.decode(
                damaged
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun twelveErasuresAreCorrected() {

        val original =
            createData()

        val damaged =
            Rs31219Encoder
                .encode(original)
                .copyOf()

        val positions =
            intArrayOf(
                0, 2, 4, 6,
                8, 10, 12, 14,
                18, 22, 26, 30
            )

        positions.forEachIndexed {
                index,
                position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                (0x21 + index)
                        ).toByte()
        }

        val recovered =
            Rs3119ErrorsErasuresDecoder.decode(
                received = damaged,
                erasurePositions =
                    positions
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun threeErrorsAndSixErasuresAreCorrected() {

        val original =
            createData()

        val damaged =
            Rs31219Encoder
                .encode(original)
                .copyOf()

        /*
         * 2e + s =
         *
         * 2×3 + 6 = 12
         *
         * exactly the correction boundary.
         */

        val erasures =
            intArrayOf(
                1,
                6,
                11,
                16,
                21,
                26
            )

        val unknownErrors =
            intArrayOf(
                4,
                14,
                30
            )

        erasures.forEachIndexed {
                index,
                position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                (0x31 + index)
                        ).toByte()
        }

        unknownErrors.forEachIndexed {
                index,
                position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                (0x51 + index)
                        ).toByte()
        }

        val recovered =
            Rs3119ErrorsErasuresDecoder.decode(
                received = damaged,
                erasurePositions =
                    erasures
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    private fun createData(): ByteArray {

        return ByteArray(
            Rs31219Encoder.DATA_BYTES
        ) {
            ((it * 73 + 19) and 0xFF)
                .toByte()
        }
    }
}