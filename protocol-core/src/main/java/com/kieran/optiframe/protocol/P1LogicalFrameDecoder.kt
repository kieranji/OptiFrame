package com.kieran.optiframe.protocol

/** Decodes all explicitly placed occupied P1 data slots from one logical frame. */
object P1LogicalFrameDecoder {
    fun decode(
        frame: P1LogicalOpticalFrame,
        positions: List<P1SlotPosition>,
        fullGridConfidence: DoubleArray? = null,
        erasureThreshold: Double = 0.0
    ): P1FrameDecodeResult {
        require(frame.header.profileId == ProtocolConstants.PROFILE_P1_ID)
        require(erasureThreshold in 0.0..1.0)
        P1DataRegionExtractor.validatePositions(positions)
        fullGridConfidence?.let {
            require(it.size == P1PhysicalLayout.GRID_WIDTH * P1PhysicalLayout.GRID_HEIGHT)
            require(it.all { confidence -> confidence.isFinite() && confidence in 0.0..1.0 })
        }

        val decoded = ArrayList<P1FrameSlotDecodeResult>(positions.size)
        val failures = ArrayList<P1SlotDecodeFailure>()
        for (position in positions) {
            try {
                val slot = P1DataRegionExtractor.extract(
                    canvas = frame.canvas,
                    positions = listOf(position)
                ).single()
                val localConfidence = fullGridConfidence?.let {
                    extractSlotConfidence(it, slot)
                } ?: DoubleArray(ProtocolConstants.OF8_MACROBLOCK_CELLS) { 1.0 }
                decoded += P1FrameSlotDecoder.decode(
                    globalHeader = frame.header,
                    slot = slot,
                    spatialCellConfidence = localConfidence,
                    erasureThreshold = erasureThreshold
                )
            } catch (failure: IllegalArgumentException) {
                failures += P1SlotDecodeFailure(position.slotIndex, failure.message ?: "Invalid slot")
            } catch (failure: IllegalStateException) {
                failures += P1SlotDecodeFailure(position.slotIndex, failure.message ?: "Undecodable slot")
            }
        }
        return P1FrameDecodeResult(decoded, failures)
    }

    private fun extractSlotConfidence(
        gridConfidence: DoubleArray,
        slot: P1LogicalFrameSlot
    ): DoubleArray {
        val originX = P1PhysicalLayout.DATA_REGION_X +
            slot.macroblockColumn * P1PhysicalLayout.MACROBLOCK_WIDTH
        val originY = P1PhysicalLayout.DATA_REGION_Y +
            slot.macroblockRow * P1PhysicalLayout.MACROBLOCK_HEIGHT
        return DoubleArray(ProtocolConstants.OF8_MACROBLOCK_CELLS) { index ->
            val row = index / ProtocolConstants.OF8_MACROBLOCK_WIDTH
            val column = index % ProtocolConstants.OF8_MACROBLOCK_WIDTH
            gridConfidence[(originY + row) * P1PhysicalLayout.GRID_WIDTH + originX + column]
        }
    }
}
