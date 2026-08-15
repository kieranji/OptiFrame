package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WhiteningTest {

    @Test
    fun seedDerivationMatchesPc001Vector() {
        val seed =
            WhiteningSeed.derive(
                sessionId = 0x12345678L,
                frameSequence = 0x9ABCDEF0L,
                slotIndex = 0x05
            )

        assertEquals(
            0xE40F61B71A06B872uL,
            seed
        )
    }

    @Test
    fun splitMix64StreamMatchesPc001Vector() {
        val generator =
            SplitMix64(
                0xE40F61B71A06B872uL
            )

        val actual =
            ByteArray(32)

        var offset = 0

        repeat(4) {
            generator
                .nextBytesLittleEndian()
                .copyInto(actual, offset)

            offset += 8
        }

        val expected =
            byteArrayOf(
                0xC9.toByte(), 0x3A, 0x87.toByte(), 0x2D,
                0xB2.toByte(), 0xFD.toByte(), 0xAF.toByte(), 0x0E,

                0x5F, 0x03, 0xBB.toByte(), 0x8A.toByte(),
                0xA7.toByte(), 0xDC.toByte(), 0x57, 0x7A,

                0x85.toByte(), 0x02, 0x40, 0x32,
                0x24, 0x62, 0x83.toByte(), 0xEB.toByte(),

                0xBF.toByte(), 0xF1.toByte(), 0x82.toByte(), 0x39,
                0x2B, 0x3D, 0xE2.toByte(), 0x9E.toByte()
            )

        assertContentEquals(
            expected,
            actual
        )
    }

    @Test
    fun whiteningIsPerfectlyReversible() {
        val original =
            ByteArray(
                ProtocolConstants.OF8_PLAIN_OBJECT_BYTES
            ) {
                it.toByte()
            }

        val whitened =
            Whitening.apply(
                data = original,
                sessionId = 0x12345678L,
                frameSequence = 100,
                slotIndex = 3
            )

        val recovered =
            Whitening.apply(
                data = whitened,
                sessionId = 0x12345678L,
                frameSequence = 100,
                slotIndex = 3
            )

        assertContentEquals(
            original,
            recovered
        )
    }
}