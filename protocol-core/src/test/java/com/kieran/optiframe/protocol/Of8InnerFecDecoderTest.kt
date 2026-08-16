package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals

class Of8InnerFecDecoderTest {

    @Test
    fun cleanOf8InnerFecRoundTrip() {

        val original =
            createObject272()

        val encoded =
            Of8InnerFecEncoder.encode(
                original
            )

        val decoded =
            Of8InnerFecDecoder.decode(
                encoded
            )

        assertContentEquals(
            original,
            decoded
        )
    }

    @Test
    fun errorsInBothCodewordsAreCorrected() {

        val original =
            createObject272()

        val encoded =
            Of8InnerFecEncoder.encode(
                original
            )

        /*
         * Deinterleave first so that we control exactly
         * how many errors are injected into each
         * shortened RS(168,136) codeword.
         */
        val (
            first,
            second
        ) =
            ByteInterleaver.deinterleaveTwo(
                encoded
            )

        val damagedFirst =
            first.copyOf()

        val damagedSecond =
            second.copyOf()

        val firstErrors =
            intArrayOf(
                4,
                23,
                51,
                78,
                109,
                144,
                166
            )

        val secondErrors =
            intArrayOf(
                2,
                31,
                62,
                95,
                130,
                151,
                167
            )

        firstErrors.forEachIndexed { index, position ->

            damagedFirst[position] =
                (
                        damagedFirst[position].toInt() xor
                                (0x31 + index)
                        ).toByte()
        }

        secondErrors.forEachIndexed { index, position ->

            damagedSecond[position] =
                (
                        damagedSecond[position].toInt() xor
                                (0x41 + index)
                        ).toByte()
        }

        val damaged336 =
            ByteInterleaver.interleaveTwo(
                damagedFirst,
                damagedSecond
            )

        val recovered =
            Of8InnerFecDecoder.decode(
                damaged336
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun sixteenErrorsPerCodewordAreCorrected() {

        val original =
            createObject272()

        val encoded =
            Of8InnerFecEncoder.encode(
                original
            )

        val (
            first,
            second
        ) =
            ByteInterleaver.deinterleaveTwo(
                encoded
            )

        val damagedFirst =
            first.copyOf()

        val damagedSecond =
            second.copyOf()

        val positions =
            intArrayOf(
                0, 10, 20, 30,
                40, 50, 60, 70,
                80, 90, 100, 110,
                120, 135, 150, 167
            )

        positions.forEachIndexed { index, position ->

            damagedFirst[position] =
                (
                        damagedFirst[position].toInt() xor
                                ((index + 1) * 3)
                        ).toByte()

            damagedSecond[position] =
                (
                        damagedSecond[position].toInt() xor
                                ((index + 1) * 5)
                        ).toByte()
        }

        val damaged336 =
            ByteInterleaver.interleaveTwo(
                damagedFirst,
                damagedSecond
            )

        val recovered =
            Of8InnerFecDecoder.decode(
                damaged336
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    private fun createObject272(): ByteArray {

        return ByteArray(
            ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
        ) {
            ((it * 113 + 29) and 0xFF)
                .toByte()
        }
    }
}