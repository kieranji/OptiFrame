package com.kieran.optiframe.protocol

/** Extracts OF8 matrices from a canvas using an explicit slot placement table. */
object P1DataRegionExtractor {
    fun extract(
        canvas: P1FrameCanvas,
        positions: List<P1SlotPosition>
    ): List<P1LogicalFrameSlot> {
        validatePositions(positions)
        return positions.map { position ->
            val cells = IntArray(ProtocolConstants.OF8_MACROBLOCK_CELLS)
            val originX = P1PhysicalLayout.DATA_REGION_X +
                position.macroblockColumn * P1PhysicalLayout.MACROBLOCK_WIDTH
            val originY = P1PhysicalLayout.DATA_REGION_Y +
                position.macroblockRow * P1PhysicalLayout.MACROBLOCK_HEIGHT
            for (row in 0 until ProtocolConstants.OF8_MACROBLOCK_HEIGHT) {
                for (column in 0 until ProtocolConstants.OF8_MACROBLOCK_WIDTH) {
                    val symbol = canvas.symbolAt(originX + column, originY + row)
                        ?: error("P1 slot ${position.slotIndex} has an empty cell")
                    cells[row * ProtocolConstants.OF8_MACROBLOCK_WIDTH + column] = symbol
                }
            }
            P1LogicalFrameSlot(
                slotIndex = position.slotIndex,
                macroblockColumn = position.macroblockColumn,
                macroblockRow = position.macroblockRow,
                matrix = Of8LogicalMatrix(cells)
            )
        }
    }

    fun validatePositions(positions: List<P1SlotPosition>) {
        require(positions.size <= ProtocolConstants.P1_MACROBLOCK_COUNT)
        require(positions.map { it.slotIndex }.toSet().size == positions.size) {
            "P1 slot mapping contains duplicate slot indices"
        }
        require(positions.map { it.macroblockColumn to it.macroblockRow }.toSet().size == positions.size) {
            "P1 slot mapping contains duplicate physical positions"
        }
    }
}
