package com.kieran.optiframe.protocol

/** Frozen PC-007 V1 coordinates, expressed in zero-based logical cells. */
object P1ControlBandLayout {
    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    val leftHeader = Rect(0, 40, 16, 16)
    val rightHeader = Rect(144, 40, 16, 16)
    val topTiming = Rect(16, 2, 128, 1)
    val bottomTiming = Rect(16, 93, 128, 1)
    val leftPilots = Rect(4, 24, 8, 4)
    val rightPilots = Rect(148, 24, 8, 4)
    val anchors = listOf(
        Rect(5, 5, 6, 6), Rect(149, 5, 6, 6),
        Rect(5, 85, 6, 6), Rect(149, 85, 6, 6)
    )

    /** Returns the OF8 palette label for a cell in the 8×4 pilot block. */
    fun pilotLabel(localX: Int, localY: Int): Int {
        require(localX in 0 until 8 && localY in 0 until 4)
        return (localY / 2) * 4 + localX / 2
    }

    /** Black outer ring, white 4×4 interior, black 2×2 center (0=black, 7=white). */
    fun anchorLabel(localX: Int, localY: Int): Int {
        require(localX in 0 until 6 && localY in 0 until 6)
        val border = localX == 0 || localX == 5 || localY == 0 || localY == 5
        val center = localX in 2..3 && localY in 2..3
        return if (border || center) 0 else 7
    }

    /** Global-header matrix bits mapped to binary OF8 labels 0 and 7. */
    fun headerLabel(matrix: GlobalHeaderLogicalMatrix, localX: Int, localY: Int): Int {
        require(localX in 0 until 16 && localY in 0 until 16)
        return if (matrix[localY, localX] == 0) 0 else 7
    }
}
