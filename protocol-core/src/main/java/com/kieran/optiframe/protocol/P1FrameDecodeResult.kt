package com.kieran.optiframe.protocol

/** Successfully decoded slots and their supplied physical identities. */
data class P1FrameDecodeResult(
    val slots: List<P1FrameSlotDecodeResult>,
    val failures: List<P1SlotDecodeFailure>
) {
    val macroblocks: List<Of8Macroblock>
        get() = slots.map { it.macroblock }

    val allProvidedSlotsDecoded: Boolean
        get() = failures.isEmpty()
}

data class P1SlotDecodeFailure(
    val slotIndex: Int,
    val reason: String
)
