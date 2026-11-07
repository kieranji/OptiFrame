package com.kieran.optiframe.protocol

/** Places explicitly positioned OF8 macroblocks in the canonical P1 data area. */
object P1DataRegionComposer {
    /**
     * Composes a frame without assigning any control patterns.
     *
     * Empty macroblock positions remain DATA_UNUSED. Every cell outside the
     * canonical P1 data area remains CONTROL_RESERVED.
     */
    fun compose(slots: List<P1LogicalFrameSlot>): P1FrameCanvas {
        require(slots.size <= ProtocolConstants.P1_MACROBLOCK_COUNT) {
            "P1 frame cannot contain more than " +
                "${ProtocolConstants.P1_MACROBLOCK_COUNT} macroblocks"
        }

        val slotIndices = HashSet<Int>()
        val positions = HashSet<Pair<Int, Int>>()
        val cells = IntArray(
            P1PhysicalLayout.GRID_WIDTH * P1PhysicalLayout.GRID_HEIGHT
        ) { P1FrameCanvas.EMPTY_CELL }

        for (slot in slots) {
            require(slotIndices.add(slot.slotIndex)) {
                "Duplicate slotIndex in P1 frame: ${slot.slotIndex}"
            }

            val position = slot.macroblockColumn to slot.macroblockRow
            require(positions.add(position)) {
                "Multiple macroblocks target column ${slot.macroblockColumn}, " +
                    "row ${slot.macroblockRow}"
            }

            val originX = P1PhysicalLayout.DATA_REGION_X +
                slot.macroblockColumn * P1PhysicalLayout.MACROBLOCK_WIDTH
            val originY = P1PhysicalLayout.DATA_REGION_Y +
                slot.macroblockRow * P1PhysicalLayout.MACROBLOCK_HEIGHT

            for (row in 0 until ProtocolConstants.OF8_MACROBLOCK_HEIGHT) {
                for (column in 0 until ProtocolConstants.OF8_MACROBLOCK_WIDTH) {
                    val x = originX + column
                    val y = originY + row
                    cells[y * P1PhysicalLayout.GRID_WIDTH + x] =
                        slot.matrix[row, column]
                }
            }
        }

        return P1FrameCanvas(cells)
    }
}
