package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Of8SymbolCodecTest {

    @Test
    fun producesExactly896Symbols() {

        val input =
            ByteArray(
                Of8SymbolCodec.INPUT_BYTES
            )

        val symbols =
            Of8SymbolCodec.bytesToSymbols(input)

        assertEquals(
            Of8SymbolCodec.SYMBOL_COUNT,
            symbols.size
        )
    }

    @Test
    fun allSymbolsAreThreeBitValues() {

        val input =
            ByteArray(
                Of8SymbolCodec.INPUT_BYTES
            ) {
                ((it * 73 + 19) and 0xFF)
                    .toByte()
            }

        val symbols =
            Of8SymbolCodec.bytesToSymbols(input)

        assertTrue(
            symbols.all {
                it in 0..7
            }
        )
    }

    @Test
    fun byteToSymbolOrderIsMsbFirst() {

        /*
         * First three bytes:
         *
         * CA = 11001010
         * 74 = 01110100
         * 1B = 00011011
         *
         * Combined:
         *
         * 110 010 100 111 010 000 011 011
         *
         * Therefore symbols:
         *
         * 6, 2, 4, 7, 2, 0, 3, 3
         */

        val input =
            ByteArray(
                Of8SymbolCodec.INPUT_BYTES
            )

        input[0] = 0xCA.toByte()
        input[1] = 0x74
        input[2] = 0x1B

        val symbols =
            Of8SymbolCodec.bytesToSymbols(input)

        assertContentEquals(
            intArrayOf(
                6,
                2,
                4,
                7,
                2,
                0,
                3,
                3
            ),
            symbols.copyOfRange(0, 8)
        )
    }

    @Test
    fun symbolsRoundTripExactly() {

        val original =
            ByteArray(
                Of8SymbolCodec.INPUT_BYTES
            ) {
                ((it * 131 + 47) and 0xFF)
                    .toByte()
            }

        val symbols =
            Of8SymbolCodec.bytesToSymbols(
                original
            )

        val recovered =
            Of8SymbolCodec.symbolsToBytes(
                symbols
            )

        assertContentEquals(
            original,
            recovered
        )
    }

    @Test
    fun zeroBytesProduceZeroSymbols() {

        val original =
            ByteArray(
                Of8SymbolCodec.INPUT_BYTES
            )

        val symbols =
            Of8SymbolCodec.bytesToSymbols(
                original
            )

        assertTrue(
            symbols.all { it == 0 }
        )
    }

    @Test
    fun invalidSymbolIsRejected() {

        val symbols =
            IntArray(
                Of8SymbolCodec.SYMBOL_COUNT
            )

        symbols[100] = 8

        assertFailsWith<IllegalArgumentException> {
            Of8SymbolCodec.symbolsToBytes(
                symbols
            )
        }
    }
}