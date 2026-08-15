package com.kieran.optiframe.protocol

object WhiteningSeed {

    fun derive(
        sessionId: Long,
        frameSequence: Long,
        slotIndex: Int
    ): ULong {
        require(sessionId in 0..0xFFFF_FFFFL) {
            "sessionId must fit uint32: $sessionId"
        }

        require(frameSequence in 0..0xFFFF_FFFFL) {
            "frameSequence must fit uint32: $frameSequence"
        }

        require(slotIndex in 0..0xFF) {
            "slotIndex must fit uint8: $slotIndex"
        }

        val input = ByteArray(11)

        BinaryIo.writeUInt32BE(sessionId)
            .copyInto(input, 0)

        BinaryIo.writeUInt32BE(frameSequence)
            .copyInto(input, 4)

        input[8] = slotIndex.toByte()

        // ASCII "OF"
        input[9] = 0x4F
        input[10] = 0x46

        val digest = Sha256.digest(input)

        var seed = 0uL

        for (i in 0 until 8) {
            seed =
                (seed shl 8) or
                        (digest[i].toULong() and 0xFFu)
        }

        return seed
    }
}