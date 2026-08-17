package com.kieran.optiframe.protocol

class GlobalHeaderLogicalMatrix(
    cellsInput: IntArray
) {

    val cells: IntArray =
        cellsInput.copyOf()

    init {
        require(
            cells.size ==
                    ProtocolConstants.GLOBAL_HEADER_MATRIX_CELLS
        ) {
            "Global Header matrix requires exactly " +
                    "${ProtocolConstants.GLOBAL_HEADER_MATRIX_CELLS} cells"
        }

        require(
            cells.all { it == 0 || it == 1 }
        ) {
            "Global Header cells must be binary"
        }
    }

    val width: Int
        get() =
            ProtocolConstants.GLOBAL_HEADER_MATRIX_WIDTH

    val height: Int
        get() =
            ProtocolConstants.GLOBAL_HEADER_MATRIX_HEIGHT

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