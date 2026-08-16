package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals

class Of8InnerFecErrorsErasuresDecoderTest {

    @Test
    fun mixedErrorsAndErasuresInBothCodewordsRecover() {

        val original =
            ByteArray(
                ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
            ) {
                ((it * 113 + 29) and 0xFF)
                    .toByte()
            }

        val encoded336 =
            Of8InnerFecEncoder.encode(
                original
            )

        val (
            first,
            second
        ) =
            ByteInterleaver.deinterleaveTwo(
                encoded336
            )

        val damagedFirst =
            first.copyOf()

        val damagedSecond =
            second.copyOf()

        /*
         * Each shortened codeword independently uses:
         *
         * e = 5
         * s = 22
         *
         * 2e + s = 32
         *
         * exactly on the V1 correction boundary.
         */

        val firstErasures =
            IntArray(22) { index ->
                index * 5
            }

        val secondErasures =
            IntArray(22) { index ->
                index * 5 + 1
            }

        val firstUnknownErrors =
            intArrayOf(
                112,
                123,
                134,
                145,
                166
            )

        val secondUnknownErrors =
            intArrayOf(
                113,
                124,
                135,
                146,
                167
            )

        firstErasures.forEachIndexed { index, position ->

            damagedFirst[position] =
                (
                        damagedFirst[position].toInt() xor
                                nonZeroMagnitude(index + 1)
                        ).toByte()
        }

        secondErasures.forEachIndexed { index, position ->

            damagedSecond[position] =
                (
                        damagedSecond[position].toInt() xor
                                nonZeroMagnitude(index + 41)
                        ).toByte()
        }

        firstUnknownErrors.forEachIndexed { index, position ->

            damagedFirst[position] =
                (
                        damagedFirst[position].toInt() xor
                                nonZeroMagnitude(index + 81)
                        ).toByte()
        }

        secondUnknownErrors.forEachIndexed { index, position ->

            damagedSecond[position] =
                (
                        damagedSecond[position].toInt() xor
                                nonZeroMagnitude(index + 101)
                        ).toByte()
        }

        val damaged336 =
            ByteInterleaver.interleaveTwo(
                damagedFirst,
                damagedSecond
            )

        /*
         * Convert shortened-codeword erasure positions back
         * into positions in the interleaved 336 B stream.
         *
         * cw0[i] -> 2*i
         * cw1[i] -> 2*i + 1
         */
        val interleavedErasures =
            IntArray(
                firstErasures.size +
                        secondErasures.size
            )

        var outputIndex = 0

        for (position in firstErasures) {

            interleavedErasures[outputIndex++] =
                position * 2
        }

        for (position in secondErasures) {

            interleavedErasures[outputIndex++] =
                position * 2 + 1
        }

        val recovered =
            Of8InnerFecErrorsErasuresDecoder.decode(
                interleaved336 = damaged336,
                erasurePositions =
                    interleavedErasures
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun cleanRoundTripStillWorks() {

        val original =
            ByteArray(
                ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
            ) {
                ((it * 37 + 11) and 0xFF)
                    .toByte()
            }

        val encoded =
            Of8InnerFecEncoder.encode(
                original
            )

        val decoded =
            Of8InnerFecErrorsErasuresDecoder.decode(
                interleaved336 = encoded
            )

        assertContentEquals(
            original,
            decoded
        )
    }

    private fun nonZeroMagnitude(
        seed: Int
    ): Int {

        val value =
            (seed * 29 + 17) and 0xFF

        return if (value == 0) {
            1
        } else {
            value
        }
    }
}