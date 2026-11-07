package com.kieran.optiframe.protocol

/** A physical P1 label grid. DATA_UNUSED cells are represented by null. */
class P1RenderedLogicalFrame internal constructor(private val labels: IntArray) {
    val width: Int = P1PhysicalLayout.GRID_WIDTH
    val height: Int = P1PhysicalLayout.GRID_HEIGHT

    init {
        require(labels.size == width * height)
        require(labels.all { it == P1FrameCanvas.EMPTY_CELL || it in 0..7 })
    }

    fun labelAt(x: Int, y: Int): Int? {
        require(x in 0 until width && y in 0 until height)
        val label = labels[y * width + x]
        return if (label == P1FrameCanvas.EMPTY_CELL) null else label
    }

    fun labelsCopy(): IntArray = labels.copyOf()
}
