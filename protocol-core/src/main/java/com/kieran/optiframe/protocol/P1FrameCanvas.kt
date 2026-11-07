package com.kieran.optiframe.protocol

/** A P1 logical grid containing OF8 symbols and empty reserved cells. */
class P1FrameCanvas internal constructor(
    private val symbols: IntArray
) {
    val width: Int = P1PhysicalLayout.GRID_WIDTH
    val height: Int = P1PhysicalLayout.GRID_HEIGHT

    init {
        require(symbols.size == width * height) {
            "P1 canvas requires exactly ${width * height} cells"
        }
        require(symbols.all { it == EMPTY_CELL || it in 0..7 }) {
            "P1 canvas cells must be empty or contain an OF8 symbol in 0..7"
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                require(
                    P1PhysicalLayout.isDataRegionCell(x, y) ||
                        symbols[indexOf(x, y)] == EMPTY_CELL
                ) {
                    "Control-reserved cells cannot contain OF8 data"
                }
            }
        }
    }

    /** Returns the OF8 symbol, or null for an unused/reserved cell. */
    fun symbolAt(x: Int, y: Int): Int? {
        requireInBounds(x, y)
        val symbol = symbols[indexOf(x, y)]
        return if (symbol == EMPTY_CELL) null else symbol
    }

    /** Returns the fixed or currently occupied role of a logical cell. */
    fun roleAt(x: Int, y: Int): P1PhysicalLayout.CellRole {
        val symbol = symbolAt(x, y)
        return P1PhysicalLayout.roleAt(
            x = x,
            y = y,
            dataCellOccupied = symbol != null
        )
    }

    private fun requireInBounds(x: Int, y: Int) {
        require(x in 0 until width) { "x is outside the P1 grid: $x" }
        require(y in 0 until height) { "y is outside the P1 grid: $y" }
    }

    private fun indexOf(x: Int, y: Int): Int = y * width + x

    companion object {
        internal const val EMPTY_CELL: Int = -1
    }
}
