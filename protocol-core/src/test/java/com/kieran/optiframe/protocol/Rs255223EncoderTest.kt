package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Rs255223EncoderTest {

    @Test
    fun zeroMessageProducesZeroParity() {
        val data =
            ByteArray(
                Rs255223Encoder.DATA_BYTES
            )

        val codeword =
            Rs255223Encoder.encode(data)

        assertEquals(
            Rs255223Encoder.CODEWORD_BYTES,
            codeword.size
        )

        assertContentEquals(
            ByteArray(
                Rs255223Encoder.CODEWORD_BYTES
            ),
            codeword
        )
    }

    @Test
    fun systematicPartIsUnchanged() {
        val data =
            ByteArray(
                Rs255223Encoder.DATA_BYTES
            ) {
                it.toByte()
            }

        val codeword =
            Rs255223Encoder.encode(data)

        assertContentEquals(
            data,
            codeword.copyOfRange(
                0,
                Rs255223Encoder.DATA_BYTES
            )
        )
    }

    @Test
    fun knownParityVectorMatches() {
        val data =
            ByteArray(
                Rs255223Encoder.DATA_BYTES
            ) {
                it.toByte()
            }

        val parity =
            Rs255223Encoder.parity(data)

        val expected =
            hexToBytes(
                "41841183b11fdb537421939696cda70e" +
                        "1db5c86684af222564b89cc6069f172e"
            )

        assertContentEquals(
            expected,
            parity
        )
    }

    @Test
    fun encodedCodewordHasZeroSyndromes() {
        val data =
            ByteArray(
                Rs255223Encoder.DATA_BYTES
            ) {
                ((it * 73 + 19) and 0xFF).toByte()
            }

        val codeword =
            Rs255223Encoder.encode(data)

        val syndromes =
            Rs255223Encoder.syndromes(codeword)

        assertTrue(
            syndromes.all { it == 0 },
            "Valid RS codeword must have zero syndromes"
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)

        return ByteArray(hex.length / 2) { index ->
            hex.substring(
                index * 2,
                index * 2 + 2
            )
                .toInt(16)
                .toByte()
        }
    }
}