package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Rs168136EncoderTest {

    @Test
    fun encodedLengthIs168Bytes() {
        val data =
            ByteArray(
                Rs168136Encoder.DATA_BYTES
            )

        val encoded =
            Rs168136Encoder.encode(data)

        assertEquals(
            Rs168136Encoder.CODEWORD_BYTES,
            encoded.size
        )
    }

    @Test
    fun shortenedCodeIsSystematic() {
        val data =
            ByteArray(
                Rs168136Encoder.DATA_BYTES
            ) {
                it.toByte()
            }

        val encoded =
            Rs168136Encoder.encode(data)

        assertContentEquals(
            data,
            encoded.copyOfRange(
                0,
                Rs168136Encoder.DATA_BYTES
            )
        )
    }

    @Test
    fun zeroInputProducesZeroCodeword() {
        val data =
            ByteArray(
                Rs168136Encoder.DATA_BYTES
            )

        val encoded =
            Rs168136Encoder.encode(data)

        assertContentEquals(
            ByteArray(
                Rs168136Encoder.CODEWORD_BYTES
            ),
            encoded
        )
    }

    @Test
    fun shortenedCodewordMatchesMotherCode() {
        val data =
            ByteArray(
                Rs168136Encoder.DATA_BYTES
            ) {
                ((it * 37 + 11) and 0xFF).toByte()
            }

        val shortened =
            Rs168136Encoder.encode(data)

        val padded =
            ByteArray(
                Rs255223Encoder.DATA_BYTES
            )

        data.copyInto(
            destination = padded,
            destinationOffset = 87
        )

        val full =
            Rs255223Encoder.encode(padded)

        assertContentEquals(
            full.copyOfRange(87, 255),
            shortened
        )
    }

    @Test
    fun reconstructedMotherCodeHasZeroSyndromes() {
        val data =
            ByteArray(
                Rs168136Encoder.DATA_BYTES
            ) {
                ((it * 73 + 19) and 0xFF).toByte()
            }

        val shortened =
            Rs168136Encoder.encode(data)

        // Receiver conceptually restores the 87 known zero bytes.
        val reconstructed =
            ByteArray(
                Rs255223Encoder.CODEWORD_BYTES
            )

        shortened.copyInto(
            destination = reconstructed,
            destinationOffset = 87
        )

        val syndromes =
            Rs255223Encoder.syndromes(
                reconstructed
            )

        assertTrue(
            syndromes.all { it == 0 },
            "Reconstructed RS(255,223) codeword must have zero syndromes"
        )
    }
}