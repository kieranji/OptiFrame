package com.kieran.optiframe.protocol

data class Of8LogicalMatrix(
    val cells: IntArray
) {

    init {
        require(
            cells.size ==
                    ProtocolConstants.OF8_MACROBLOCK_CELLS
        ) {
            "OF8 matrix requires exactly " +
                    "${ProtocolConstants.OF8_MACROBLOCK_CELLS} cells"
        }

        require(
            cells.all { it in 0..7 }
        ) {
            "Every OF8 cell must be in range 0..7"
        }
    }

    val width: Int
        get() =
            ProtocolConstants.OF8_MACROBLOCK_WIDTH

    val height: Int
        get() =
            ProtocolConstants.OF8_MACROBLOCK_HEIGHT

    operator fun get(
        row: Int,
        column: Int
    ): Int {

        require(row in 0 until height)
        require(column in 0 until width)

        return cells[
            row * width + column
        ]
    }
}