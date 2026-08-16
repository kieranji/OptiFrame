package com.kieran.optiframe.protocol

data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int
) {
    init {
        require(red in 0..255)
        require(green in 0..255)
        require(blue in 0..255)
    }
}