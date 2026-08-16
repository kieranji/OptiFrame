package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals

class Rs255223ErrorsErasuresDecoderTest {

    @Test
    fun fiveErrorsAndTwentyTwoErasuresRecover() {

        runCase(
            unknownErrors = 5,
            erasures = 22
        )
    }

    @Test
    fun eightErrorsAndSixteenErasuresRecover() {

        runCase(
            unknownErrors = 8,
            erasures = 16
        )
    }

    @Test
    fun twelveErrorsAndEightErasuresRecover() {

        runCase(
            unknownErrors = 12,
            erasures = 8
        )
    }

    @Test
    fun sixteenUnknownErrorsRecover() {

        runCase(
            unknownErrors = 16,
            erasures = 0
        )
    }

    @Test
    fun thirtyTwoErasuresRecover() {

        runCase(
            unknownErrors = 0,
            erasures = 32
        )
    }

    private fun runCase(
        unknownErrors: Int,
        erasures: Int
    ) {

        require(
            2 * unknownErrors + erasures <= 32
        )

        val data =
            ByteArray(
                Rs255223Encoder.DATA_BYTES
            ) {
                ((it * 73 + 19) and 0xFF)
                    .toByte()
            }

        val original =
            Rs255223Encoder.encode(
                data
            )

        val damaged =
            original.copyOf()

        /*
         * Deterministic distinct positions.
         *
         * This keeps the test reproducible.
         */
        val total =
            unknownErrors + erasures

        val positions =
            IntArray(total) { index ->
                (index * 7 + 3) % 255
            }

        val erasurePositions =
            positions.copyOfRange(
                0,
                erasures
            )

        positions.forEachIndexed { index, position ->

            val magnitude =
                ((index * 29 + 17) and 0xFF)
                    .let {
                        if (it == 0) 1 else it
                    }

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                magnitude
                        ).toByte()
        }

        val recovered =
            Rs255223ErrorsErasuresDecoder.decode(
                received = damaged,
                erasurePositions =
                    erasurePositions
            )

        assertContentEquals(
            original,
            recovered
        )
    }
}