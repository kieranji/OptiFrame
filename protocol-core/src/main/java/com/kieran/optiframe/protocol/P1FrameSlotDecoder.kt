package com.kieran.optiframe.protocol

/** Result of recovering and validating one OF8 slot from a P1 frame. */
data class P1FrameSlotDecodeResult(
    val macroblock: Of8Macroblock,
    val interleavedErasures: IntArray,
    val byteConfidence: DoubleArray
)

/** Decodes one positioned OF8 slot using the matching P1 frame metadata. */
object P1FrameSlotDecoder {
    fun decode(
        globalHeader: OptiFrameGlobalHeader,
        slot: P1LogicalFrameSlot,
        spatialCellConfidence: DoubleArray =
            DoubleArray(ProtocolConstants.OF8_MACROBLOCK_CELLS) { 1.0 },
        erasureThreshold: Double = 0.0
    ): P1FrameSlotDecodeResult {
        require(globalHeader.profileId == ProtocolConstants.PROFILE_P1_ID) {
            "P1 slot decoding requires profile P1"
        }

        val expectedFrameSeqLow =
            (globalHeader.frameSequence and 0xFFFFL).toInt()
        val observed = Of8ObservedMatrixDecoder.decode(
            matrix = slot.matrix,
            spatialCellConfidence = spatialCellConfidence,
            frameSequence = globalHeader.frameSequence,
            slotIndex = slot.slotIndex,
            erasureThreshold = erasureThreshold
        )

        val whitenedObject = Of8InnerFecErrorsErasuresDecoder.decode(
            interleaved336 = observed.interleaved336,
            erasurePositions = observed.erasurePositions
        )
        val plainObject = Whitening.apply(
            data = whitenedObject,
            sessionId = globalHeader.sessionId,
            frameSequence = globalHeader.frameSequence,
            slotIndex = slot.slotIndex
        )
        val macroblock = Of8MacroblockCodec.decode(plainObject)

        require(macroblock.header.slotIndex == slot.slotIndex) {
            "Decoded macroblock slotIndex does not match its frame slot"
        }
        require(macroblock.header.frameSeqLow == expectedFrameSeqLow) {
            "Decoded macroblock frameSeqLow does not match the global header"
        }

        return P1FrameSlotDecodeResult(
            macroblock = macroblock,
            interleavedErasures = observed.erasurePositions,
            byteConfidence = observed.byteConfidence
        )
    }
}
