package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Of8PaletteTest {

    @Test
    fun allEightSymbolsMatchExpectedColors() {

        val expected =
            arrayOf(
                RgbColor(32, 32, 32),
                RgbColor(32, 32, 224),
                RgbColor(32, 224, 32),
                RgbColor(32, 224, 224),
                RgbColor(224, 32, 32),
                RgbColor(224, 32, 224),
                RgbColor(224, 224, 32),
                RgbColor(224, 224, 224)
            )

        for (symbol in 0..7) {

            assertEquals(
                expected[symbol],
                Of8Palette.symbolToColor(symbol)
            )
        }
    }

    @Test
    fun allEightColorsRoundTrip() {

        for (symbol in 0..7) {

            val color =
                Of8Palette.symbolToColor(symbol)

            val recovered =
                Of8Palette.colorToSymbol(color)

            assertEquals(
                symbol,
                recovered
            )
        }
    }

    @Test
    fun invalidSymbolIsRejected() {

        assertFailsWith<IllegalArgumentException> {
            Of8Palette.symbolToColor(8)
        }
    }

    @Test
    fun nonPaletteColorIsRejectedByExactDecoder() {

        assertFailsWith<IllegalArgumentException> {
            Of8Palette.colorToSymbol(
                RgbColor(
                    red = 100,
                    green = 32,
                    blue = 224
                )
            )
        }
    }
}