package com.kieran.optiframe.protocol

/** Converts logical cells to row-major RGB888 pixels for a platform renderer. */
object P1PhysicalFrameRasterizer {
    const val BYTES_PER_PIXEL = 3

    fun toRgb888(frame: P1RenderedLogicalFrame): ByteArray {
        val pixels = ByteArray(frame.width * frame.height * BYTES_PER_PIXEL)
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                // DATA_UNUSED cells use fixed palette label 0 on the display.
                val color = Of8Palette.symbolToColor(frame.labelAt(x, y) ?: 0)
                val offset = (y * frame.width + x) * BYTES_PER_PIXEL
                pixels[offset] = color.red.toByte()
                pixels[offset + 1] = color.green.toByte()
                pixels[offset + 2] = color.blue.toByte()
            }
        }
        return pixels
    }
}
