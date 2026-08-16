package com.kieran.optiframe.protocol

object ByteInterleaver {

    fun interleaveTwo(
        first: ByteArray,
        second: ByteArray
    ): ByteArray {
        require(first.size == second.size) {
            "Both arrays must have the same length"
        }

        val result =
            ByteArray(first.size * 2)

        for (i in first.indices) {
            result[i * 2] = first[i]
            result[i * 2 + 1] = second[i]
        }

        return result
    }

    fun deinterleaveTwo(
        interleaved: ByteArray
    ): Pair<ByteArray, ByteArray> {
        require(interleaved.size % 2 == 0) {
            "Interleaved byte count must be even"
        }

        val half = interleaved.size / 2

        val first = ByteArray(half)
        val second = ByteArray(half)

        for (i in 0 until half) {
            first[i] = interleaved[i * 2]
            second[i] = interleaved[i * 2 + 1]
        }

        return first to second
    }
}