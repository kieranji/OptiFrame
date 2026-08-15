package com.kieran.optiframe.protocol

class SplitMix64(
    seed: ULong
) {

    private var state: ULong = seed

    fun nextULong(): ULong {
        state += 0x9E3779B97F4A7C15uL

        var z = state

        z =
            (z xor (z shr 30)) *
                    0xBF58476D1CE4E5B9uL

        z =
            (z xor (z shr 27)) *
                    0x94D049BB133111EBuL

        return z xor (z shr 31)
    }

    fun nextBytesLittleEndian(): ByteArray {
        val value = nextULong()

        return ByteArray(8) { index ->
            ((value shr (index * 8)) and 0xFFu)
                .toByte()
        }
    }
}