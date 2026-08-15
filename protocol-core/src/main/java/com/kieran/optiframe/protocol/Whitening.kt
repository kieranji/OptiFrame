package com.kieran.optiframe.protocol

object Whitening {

    fun apply(
        data: ByteArray,
        sessionId: Long,
        frameSequence: Long,
        slotIndex: Int
    ): ByteArray {

        val seed =
            WhiteningSeed.derive(
                sessionId = sessionId,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        val generator = SplitMix64(seed)

        val result = ByteArray(data.size)

        var offset = 0

        while (offset < data.size) {
            val randomBytes =
                generator.nextBytesLittleEndian()

            for (i in randomBytes.indices) {
                if (offset >= data.size) {
                    break
                }

                result[offset] =
                    (data[offset].toInt() xor
                            randomBytes[i].toInt())
                        .toByte()

                offset++
            }
        }

        return result
    }
}