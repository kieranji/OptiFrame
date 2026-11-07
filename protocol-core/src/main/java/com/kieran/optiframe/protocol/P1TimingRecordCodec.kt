package com.kieran.optiframe.protocol

/** PC-007 timing record encoding, including the duplicated 128-cell row form. */
object P1TimingRecordCodec {
    private const val SYNC = 0xD3
    private const val M_SEQUENCE_BITS = 31
    private const val RECORD_BITS = 64

    data class TimingRecord(val frameSequenceLow16: Int, val parity: Int)

    fun encode(frameSequence: Long): BooleanArray {
        require(frameSequence in 0..0xFFFF_FFFFL)
        val low = frameSequence.toInt() and 0xFFFF

        val bits = BooleanArray(RECORD_BITS)
        var cursor = 0
        appendByte(bits, cursor, SYNC); cursor += 8
        appendByte(bits, cursor, low ushr 8); cursor += 8
        appendByte(bits, cursor, low); cursor += 8
        bits[cursor++] = (frameSequence.toInt() and 1) != 0

        var state = 0x1F
        repeat(M_SEQUENCE_BITS) {
            bits[cursor++] = (state and 0x10) != 0
            val feedback = ((state ushr 4) xor (state ushr 1)) and 1
            state = ((state shl 1) and 0x1F) or feedback
        }
        check(cursor == 56)
        val prefix = bitsToBytes(bits, 0, 56)
        val crc = crc8Atm(prefix)
        appendByte(bits, cursor, crc)
        return bits
    }

    fun decode(record: BooleanArray): TimingRecord {
        require(record.size == RECORD_BITS)
        val bytes = bitsToBytes(record, 0, RECORD_BITS)
        require(bytes[0].toInt() and 0xFF == SYNC) { "Invalid PC-007 timing sync" }
        require(crc8Atm(bytes.copyOfRange(0, 7)) == (bytes[7].toInt() and 0xFF)) {
            "Invalid PC-007 timing CRC"
        }
        var state = 0x1F
        repeat(M_SEQUENCE_BITS) { index ->
            require(record[25 + index] == ((state and 0x10) != 0)) {
                "Invalid PC-007 m-sequence"
            }
            val feedback = ((state ushr 4) xor (state ushr 1)) and 1
            state = ((state shl 1) and 0x1F) or feedback
        }
        val low = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        val parity = if (record[24]) 1 else 0
        require(parity == (low and 1)) { "PC-007 timing parity mismatch" }
        return TimingRecord(low, parity)
    }

    /** 128 cells: identical MSB-first copies of the 64-bit timing record. */
    fun encodeRepeatedRow(frameSequence: Long): BooleanArray {
        val record = encode(frameSequence)
        return BooleanArray(128) { record[it % RECORD_BITS] }
    }

    /** Decode both row copies and reject disagreement or corruption. */
    fun decodeRepeatedRow(row: BooleanArray): TimingRecord {
        require(row.size == 128)
        val first = decode(row.copyOfRange(0, 64))
        val second = decode(row.copyOfRange(64, 128))
        require(first == second) { "PC-007 repeated timing records disagree" }
        return first
    }

    private fun crc8Atm(bytes: ByteArray): Int {
        var crc = 0
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) and 0xFF
                else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    private fun appendByte(bits: BooleanArray, offset: Int, value: Int) {
        for (i in 0 until 8) bits[offset + i] = ((value ushr (7 - i)) and 1) != 0
    }

    private fun bitsToBytes(bits: BooleanArray, start: Int, count: Int): ByteArray {
        require(count % 8 == 0)
        return ByteArray(count / 8) { byteIndex ->
            var value = 0
            repeat(8) { bit ->
                value = (value shl 1) or if (bits[start + byteIndex * 8 + bit]) 1 else 0
            }
            value.toByte()
        }
    }
}
