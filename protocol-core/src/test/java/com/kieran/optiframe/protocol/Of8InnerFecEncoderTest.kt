package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Of8InnerFecEncoderTest {

    @Test
    fun byteInterleavingRoundTripWorks() {
        val first =
            ByteArray(
                ProtocolConstants.OF8_RS_CODEWORD_BYTES
            ) {
                it.toByte()
            }

        val second =
            ByteArray(
                ProtocolConstants.OF8_RS_CODEWORD_BYTES
            ) {
                (255 - it).toByte()
            }

        val interleaved =
            ByteInterleaver.interleaveTwo(
                first,
                second
            )

        assertEquals(
            ProtocolConstants.OF8_RS_INTERLEAVED_BYTES,
            interleaved.size
        )

        val (recoveredFirst, recoveredSecond) =
            ByteInterleaver.deinterleaveTwo(
                interleaved
            )

        assertContentEquals(
            first,
            recoveredFirst
        )

        assertContentEquals(
            second,
            recoveredSecond
        )
    }

    @Test
    fun interleavingOrderMatchesProtocol() {
        val first =
            byteArrayOf(
                0x10,
                0x11,
                0x12
            )

        val second =
            byteArrayOf(
                0x20,
                0x21,
                0x22
            )

        val result =
            ByteInterleaver.interleaveTwo(
                first,
                second
            )

        assertContentEquals(
            byteArrayOf(
                0x10,
                0x20,
                0x11,
                0x21,
                0x12,
                0x22
            ),
            result
        )
    }

    @Test
    fun of8InnerFecProduces336Bytes() {
        val input =
            ByteArray(
                ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
            ) {
                ((it * 37 + 9) and 0xFF)
                    .toByte()
            }

        val encoded =
            Of8InnerFecEncoder.encode(input)

        assertEquals(
            336,
            encoded.size
        )
    }

    @Test
    fun of8InnerFecContainsExpectedCodewords() {
        val input =
            ByteArray(
                ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
            ) {
                ((it * 73 + 17) and 0xFF)
                    .toByte()
            }

        val encoded =
            Of8InnerFecEncoder.encode(input)

        val (firstCodeword, secondCodeword) =
            ByteInterleaver.deinterleaveTwo(
                encoded
            )

        val expectedFirst =
            Rs168136Encoder.encode(
                input.copyOfRange(
                    0,
                    136
                )
            )

        val expectedSecond =
            Rs168136Encoder.encode(
                input.copyOfRange(
                    136,
                    272
                )
            )

        assertContentEquals(
            expectedFirst,
            firstCodeword
        )

        assertContentEquals(
            expectedSecond,
            secondCodeword
        )
    }

    @Test
    fun reconstructedMotherCodesHaveZeroSyndromes() {
        val input =
            ByteArray(
                ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
            ) {
                ((it * 29 + 101) and 0xFF)
                    .toByte()
            }

        val encoded =
            Of8InnerFecEncoder.encode(input)

        val (first, second) =
            ByteInterleaver.deinterleaveTwo(
                encoded
            )

        verifyShortenedCodeword(first)
        verifyShortenedCodeword(second)
    }

    private fun verifyShortenedCodeword(
        shortened: ByteArray
    ) {
        val full =
            ByteArray(
                Rs255223Encoder.CODEWORD_BYTES
            )

        shortened.copyInto(
            destination = full,
            destinationOffset = 87
        )

        val syndromes =
            Rs255223Encoder.syndromes(full)

        check(
            syndromes.all { it == 0 }
        ) {
            "Reconstructed mother codeword has non-zero syndromes"
        }
    }
}