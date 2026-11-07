package com.kieran.optiframe.protocol

/** Explicit on-frame position for a protocol slot; mapping remains caller-owned. */
data class P1SlotPosition(
    val slotIndex: Int,
    val macroblockColumn: Int,
    val macroblockRow: Int
) {
    init {
        require(slotIndex in 0 until ProtocolConstants.P1_MACROBLOCK_COUNT)
        require(macroblockColumn in 0 until P1PhysicalLayout.MACROBLOCK_COLUMNS)
        require(macroblockRow in 0 until P1PhysicalLayout.MACROBLOCK_ROWS)
    }

    companion object {
        /**
         * Canonical P1 B0..B11 placement from OptiFrame V1 manual figure 9-1:
         * four macroblocks across, three down, numbered left-to-right then
         * top-to-bottom.
         */
        fun canonicalP1(): List<P1SlotPosition> = List(
            P1PhysicalLayout.MACROBLOCK_COLUMNS * P1PhysicalLayout.MACROBLOCK_ROWS
        ) { slotIndex ->
            P1SlotPosition(
                slotIndex = slotIndex,
                macroblockColumn = slotIndex % P1PhysicalLayout.MACROBLOCK_COLUMNS,
                macroblockRow = slotIndex / P1PhysicalLayout.MACROBLOCK_COLUMNS
            )
        }
    }
}
