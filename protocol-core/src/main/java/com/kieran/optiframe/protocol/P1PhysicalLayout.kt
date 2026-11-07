package com.kieran.optiframe.protocol

/**
 * Geometry that is fixed for the P1 logical optical frame.
 *
 * This class describes cell ownership and the canonical data-region geometry.
 * Frozen PC-007 control coordinates and patterns are defined by
 * [P1ControlBandLayout] and [P1TimingRecordCodec].
 */
object P1PhysicalLayout {
    const val GRID_WIDTH: Int = 160
    const val GRID_HEIGHT: Int = 96

    const val DATA_REGION_X: Int = 16
    const val DATA_REGION_Y: Int = 6
    const val DATA_REGION_WIDTH: Int = 128
    const val DATA_REGION_HEIGHT: Int = 84

    const val MACROBLOCK_WIDTH: Int = 32
    const val MACROBLOCK_HEIGHT: Int = 28
    const val MACROBLOCK_COLUMNS: Int = 4
    const val MACROBLOCK_ROWS: Int = 3

    init {
        check(DATA_REGION_WIDTH == MACROBLOCK_WIDTH * MACROBLOCK_COLUMNS)
        check(DATA_REGION_HEIGHT == MACROBLOCK_HEIGHT * MACROBLOCK_ROWS)
        check(DATA_REGION_X + DATA_REGION_WIDTH == GRID_WIDTH - DATA_REGION_X)
        check(DATA_REGION_Y + DATA_REGION_HEIGHT == GRID_HEIGHT - DATA_REGION_Y)
    }

    /** The role of a logical cell in a P1 frame. */
    enum class CellRole {
        /** A data-region cell currently carrying OF8 data. */
        OF8_DATA,

        /** A data-region cell not occupied by a data macroblock. */
        DATA_UNUSED,

        /** A cell outside the canonical data region, reserved for control. */
        CONTROL_RESERVED
    }

    /**
     * Returns whether the coordinate lies inside the fixed 128×84 data region.
     * Coordinates use a zero-based origin at the top-left of the full frame.
     */
    fun isDataRegionCell(x: Int, y: Int): Boolean {
        require(x in 0 until GRID_WIDTH) { "x is outside the P1 grid: $x" }
        require(y in 0 until GRID_HEIGHT) { "y is outside the P1 grid: $y" }

        return x >= DATA_REGION_X &&
            x < DATA_REGION_X + DATA_REGION_WIDTH &&
            y >= DATA_REGION_Y &&
            y < DATA_REGION_Y + DATA_REGION_HEIGHT
    }

    /**
     * Classifies a coordinate without assigning any unresolved control pattern.
     * Callers supply whether a data-region cell is occupied by OF8 data.
     */
    fun roleAt(x: Int, y: Int, dataCellOccupied: Boolean = false): CellRole {
        val inDataRegion = isDataRegionCell(x, y)
        require(inDataRegion || !dataCellOccupied) {
            "Only cells inside the P1 data region can carry OF8 data"
        }

        if (!inDataRegion) {
            return CellRole.CONTROL_RESERVED
        }

        return if (dataCellOccupied) {
            CellRole.OF8_DATA
        } else {
            CellRole.DATA_UNUSED
        }
    }
}
