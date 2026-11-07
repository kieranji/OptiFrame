package com.kieran.optiframe.protocol

/**
 * One OF8 macroblock and its explicitly selected position in the P1 data grid.
 *
 * The position is supplied explicitly; [P1SlotPosition.canonicalP1] provides
 * the frozen B0..B11 row-major P1 mapping.
 */
data class P1LogicalFrameSlot(
    val slotIndex: Int,
    val macroblockColumn: Int,
    val macroblockRow: Int,
    val matrix: Of8LogicalMatrix
) {
    init {
        require(slotIndex in 0 until ProtocolConstants.P1_MACROBLOCK_COUNT) {
            "slotIndex must be in 0 until ${ProtocolConstants.P1_MACROBLOCK_COUNT}"
        }
        require(macroblockColumn in 0 until P1PhysicalLayout.MACROBLOCK_COLUMNS) {
            "macroblockColumn is outside the P1 data grid"
        }
        require(macroblockRow in 0 until P1PhysicalLayout.MACROBLOCK_ROWS) {
            "macroblockRow is outside the P1 data grid"
        }
    }

    companion object {
        /**
         * Encodes one protected OF8 macroblock for this logical P1 frame.
         *
         * The macroblock's slot index remains protocol metadata; its physical
         * column and row are explicit inputs so the selected P1 placement is
         * visible at the call site.
         */
        fun fromMacroblock(
            globalHeader: OptiFrameGlobalHeader,
            macroblock: Of8Macroblock,
            macroblockColumn: Int,
            macroblockRow: Int
        ): P1LogicalFrameSlot {
            require(globalHeader.profileId == ProtocolConstants.PROFILE_P1_ID) {
                "P1 macroblocks require profile P1"
            }

            val slotIndex = macroblock.header.slotIndex
            require(slotIndex in 0 until ProtocolConstants.P1_MACROBLOCK_COUNT) {
                "P1 slotIndex must be in " +
                    "0 until ${ProtocolConstants.P1_MACROBLOCK_COUNT}"
            }

            val expectedFrameSeqLow =
                (globalHeader.frameSequence and 0xFFFFL).toInt()
            require(macroblock.header.frameSeqLow == expectedFrameSeqLow) {
                "Macroblock frameSeqLow does not match the global frame sequence"
            }

            val plainObject = Of8MacroblockCodec.encode(macroblock)
            val whitenedObject = Whitening.apply(
                data = plainObject,
                sessionId = globalHeader.sessionId,
                frameSequence = globalHeader.frameSequence,
                slotIndex = slotIndex
            )
            val interleavedCodewords = Of8InnerFecEncoder.encode(whitenedObject)
            val matrix = Of8MatrixCodec.encode(
                interleaved336 = interleavedCodewords,
                frameSequence = globalHeader.frameSequence,
                slotIndex = slotIndex
            )

            return P1LogicalFrameSlot(
                slotIndex = slotIndex,
                macroblockColumn = macroblockColumn,
                macroblockRow = macroblockRow,
                matrix = matrix
            )
        }
    }
}
