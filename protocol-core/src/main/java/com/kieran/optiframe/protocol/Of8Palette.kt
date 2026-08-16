package com.kieran.optiframe.protocol

object Of8Palette {

    private const val LOW =
        ProtocolConstants.OF8_PALETTE_LOW

    private const val HIGH =
        ProtocolConstants.OF8_PALETTE_HIGH

    fun symbolToColor(
        symbol: Int
    ): RgbColor {

        require(symbol in 0..7) {
            "OF8 symbol must be in range 0..7"
        }

        val redBit =
            (symbol ushr 2) and 0x01

        val greenBit =
            (symbol ushr 1) and 0x01

        val blueBit =
            symbol and 0x01

        return RgbColor(
            red = level(redBit),
            green = level(greenBit),
            blue = level(blueBit)
        )
    }

    fun colorToSymbol(
        color: RgbColor
    ): Int {

        val redBit =
            exactLevelToBit(color.red)

        val greenBit =
            exactLevelToBit(color.green)

        val blueBit =
            exactLevelToBit(color.blue)

        return (
                (redBit shl 2) or
                        (greenBit shl 1) or
                        blueBit
                )
    }

    fun matrixToColors(
        matrix: Of8LogicalMatrix
    ): Array<RgbColor> {

        return Array(matrix.cells.size) { index ->
            symbolToColor(
                matrix.cells[index]
            )
        }
    }

    private fun level(
        bit: Int
    ): Int {
        return if (bit == 0) {
            LOW
        } else {
            HIGH
        }
    }

    private fun exactLevelToBit(
        value: Int
    ): Int {

        return when (value) {
            LOW -> 0
            HIGH -> 1

            else -> throw IllegalArgumentException(
                "RGB value must be exactly $LOW or $HIGH, got $value"
            )
        }
    }

    fun colorsToMatrix(
        colors: Array<RgbColor>
    ): Of8LogicalMatrix {

        require(
            colors.size ==
                    ProtocolConstants.OF8_MACROBLOCK_CELLS
        ) {
            "OF8 color matrix must contain exactly " +
                    "${ProtocolConstants.OF8_MACROBLOCK_CELLS} colors"
        }

        val symbols =
            IntArray(colors.size) { index ->
                colorToSymbol(
                    colors[index]
                )
            }

        return Of8LogicalMatrix(
            cells = symbols
        )
    }
}