package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BinaryIoTest {

    @Test
    fun uint16BigEndianRoundTrip() {
        val value = 0xABCD

        val encoded = BinaryIo.writeUInt16BE(value)

        assertContentEquals(
            byteArrayOf(
                0xAB.toByte(),
                0xCD.toByte()
            ),
            encoded
        )

        assertEquals(
            value,
            BinaryIo.readUInt16BE(encoded)
        )
    }

    @Test
    fun uint32BigEndianRoundTrip() {
        val value = 0x12345678L

        val encoded = BinaryIo.writeUInt32BE(value)

        assertContentEquals(
            byteArrayOf(
                0x12,
                0x34,
                0x56,
                0x78
            ),
            encoded
        )

        assertEquals(
            value,
            BinaryIo.readUInt32BE(encoded)
        )
    }

    @Test
    fun uint64BigEndianRoundTrip() {
        val value = 0x0123456789ABCDEFuL

        val encoded = BinaryIo.writeUInt64BE(value)

        assertContentEquals(
            byteArrayOf(
                0x01,
                0x23,
                0x45,
                0x67,
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte()
            ),
            encoded
        )

        assertEquals(
            value,
            BinaryIo.readUInt64BE(encoded)
        )
    }

    @Test
    fun uint16RejectsOutOfRangeValues() {
        assertFailsWith<IllegalArgumentException> {
            BinaryIo.writeUInt16BE(-1)
        }

        assertFailsWith<IllegalArgumentException> {
            BinaryIo.writeUInt16BE(0x1_0000)
        }
    }

    @Test
    fun uint32RejectsOutOfRangeValues() {
        assertFailsWith<IllegalArgumentException> {
            BinaryIo.writeUInt32BE(-1)
        }

        assertFailsWith<IllegalArgumentException> {
            BinaryIo.writeUInt32BE(0x1_0000_0000L)
        }
    }
}