package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OptiFrameGlobalHeaderTest {

    @Test
    fun crc8AtmKnownVectorMatches() {

        assertEquals(
            0xF4,
            Crc8Atm.compute(
                "123456789".encodeToByteArray()
            )
        )
    }

    @Test
    fun rawHeaderIsExactly19Bytes() {

        val header =
            createHeader()

        val encoded =
            OptiFrameGlobalHeaderCodec.encode(
                header
            )

        assertEquals(
            19,
            encoded.size
        )

        assertEquals(
            0x4F,
            encoded[0].toInt() and 0xFF
        )

        assertEquals(
            0x46,
            encoded[1].toInt() and 0xFF
        )
    }

    @Test
    fun rawHeaderRoundTripWorks() {

        val original =
            createHeader()

        val encoded =
            OptiFrameGlobalHeaderCodec.encode(
                original
            )

        val recovered =
            OptiFrameGlobalHeaderCodec.decode(
                encoded
            )

        assertEquals(
            original,
            recovered
        )
    }

    @Test
    fun rawHeaderCorruptionFailsCrc() {

        val encoded =
            OptiFrameGlobalHeaderCodec
                .encode(createHeader())

        encoded[10] =
            (
                    encoded[10].toInt() xor
                            0x01
                    ).toByte()

        assertFailsWith<IllegalArgumentException> {
            OptiFrameGlobalHeaderCodec.decode(
                encoded
            )
        }
    }

    @Test
    fun shortenedRsKnownVectorMatches() {

        val input =
            ByteArray(19) {
                it.toByte()
            }

        val encoded =
            Rs31219Encoder.encode(
                input
            )

        assertEquals(
            "000102030405060708090a0b0c0d0e0f101112" +
                    "eaeaacedca26851ac85cf441",
            toHex(encoded)
        )
    }

    @Test
    fun protectedHeaderIsExactly31Bytes() {

        val encoded =
            OptiFrameProtectedGlobalHeaderCodec
                .encode(createHeader())

        assertEquals(
            31,
            encoded.size
        )
    }

    @Test
    fun protectedHeaderHasZeroMotherCodeSyndromes() {

        val shortened =
            OptiFrameProtectedGlobalHeaderCodec
                .encode(createHeader())

        val full =
            ByteArray(
                Rs255243Encoder.CODEWORD_BYTES
            )

        shortened.copyInto(
            destination = full,
            destinationOffset = 224
        )

        assertTrue(
            Rs255243Encoder
                .syndromes(full)
                .all { it == 0 }
        )
    }

    @Test
    fun protectedHeaderCleanRoundTripWorks() {

        val original =
            createHeader()

        val protected =
            OptiFrameProtectedGlobalHeaderCodec
                .encode(original)

        val recovered =
            OptiFrameProtectedGlobalHeaderCodec
                .cleanDecode(protected)

        assertEquals(
            original,
            recovered
        )
    }

    private fun createHeader():
            OptiFrameGlobalHeader {

        return OptiFrameGlobalHeader(
            profileId =
                ProtocolConstants.PROFILE_P1_ID,
            sessionId =
                0x12345678L,
            frameSequence =
                0x9ABCDEF0L,
            sourceBlockNumber =
                42,
            paletteId =
                ProtocolConstants.PALETTE_OF8_FIXED,
            frameType =
                ProtocolConstants.FRAME_TYPE_DATA,
            flags =
                0,
            calibrationEpoch =
                3
        )
    }

    private fun toHex(
        bytes: ByteArray
    ): String {

        return bytes.joinToString("") {
            "%02x".format(
                it.toInt() and 0xFF
            )
        }
    }

    @Test
    fun protectedHeaderRecoversSixUnknownErrors() {

        val original =
            createHeader()

        val damaged =
            OptiFrameProtectedGlobalHeaderCodec
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

        positions.forEachIndexed { index, position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                ((index + 1) * 13)
                        ).toByte()
        }

        val recovered =
            OptiFrameProtectedGlobalHeaderCodec.decode(
                damaged
            )

        assertEquals(
            original,
            recovered
        )
    }

    @Test
    fun protectedHeaderRecoversMixedErrorsAndErasures() {

        val original =
            createHeader()

        val damaged =
            OptiFrameProtectedGlobalHeaderCodec
                .encode(original)
                .copyOf()

        val erasures =
            intArrayOf(
                1,
                6,
                11,
                16,
                21,
                26
            )

        val errors =
            intArrayOf(
                4,
                14,
                30
            )

        erasures.forEachIndexed { index, position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                (0x21 + index)
                        ).toByte()
        }

        errors.forEachIndexed { index, position ->

            damaged[position] =
                (
                        damaged[position].toInt() xor
                                (0x61 + index)
                        ).toByte()
        }

        val recovered =
            OptiFrameProtectedGlobalHeaderCodec.decode(
                protected31 = damaged,
                erasurePositions =
                    erasures
            )

        assertEquals(
            original,
            recovered
        )
    }
}