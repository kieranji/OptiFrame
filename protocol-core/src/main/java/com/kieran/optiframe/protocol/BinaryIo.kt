package com.kieran.optiframe.protocol

object BinaryIo {

    fun writeUInt16BE(value: Int): ByteArray {
        require(value in 0..0xFFFF) {
            "UInt16 value out of range: $value"
        }

        return byteArrayOf(
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    fun readUInt16BE(
        bytes: ByteArray,
        offset: Int = 0
    ): Int {
        require(offset >= 0)
        require(offset + 2 <= bytes.size) {
            "Not enough bytes to read UInt16 at offset $offset"
        }

        return ((bytes[offset].toInt() and 0xFF) shl 8) or
                (bytes[offset + 1].toInt() and 0xFF)
    }

    fun writeUInt32BE(value: Long): ByteArray {
        require(value in 0..0xFFFF_FFFFL) {
            "UInt32 value out of range: $value"
        }

        return byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    fun readUInt32BE(
        bytes: ByteArray,
        offset: Int = 0
    ): Long {
        require(offset >= 0)
        require(offset + 4 <= bytes.size) {
            "Not enough bytes to read UInt32 at offset $offset"
        }

        return ((bytes[offset].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
                (bytes[offset + 3].toLong() and 0xFF)
    }

    fun writeUInt64BE(value: ULong): ByteArray {
        return byteArrayOf(
            ((value shr 56) and 0xFFu).toByte(),
            ((value shr 48) and 0xFFu).toByte(),
            ((value shr 40) and 0xFFu).toByte(),
            ((value shr 32) and 0xFFu).toByte(),
            ((value shr 24) and 0xFFu).toByte(),
            ((value shr 16) and 0xFFu).toByte(),
            ((value shr 8) and 0xFFu).toByte(),
            (value and 0xFFu).toByte()
        )
    }

    fun readUInt64BE(
        bytes: ByteArray,
        offset: Int = 0
    ): ULong {
        require(offset >= 0)
        require(offset + 8 <= bytes.size) {
            "Not enough bytes to read UInt64 at offset $offset"
        }

        var result = 0uL

        for (i in 0 until 8) {
            result =
                (result shl 8) or
                        (bytes[offset + i].toULong() and 0xFFu)
        }

        return result
    }
}