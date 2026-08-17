package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Of8FileSymbolizerTest {

    @Test
    fun zeroByteFileRoundTrip() {
        runRoundTrip(
            size = 0
        )
    }

    @Test
    fun oneByteFileRoundTrip() {
        runRoundTrip(
            size = 1
        )
    }

    @Test
    fun twoHundredFiftyFiveByteFileRoundTrip() {
        runRoundTrip(
            size = 255
        )
    }

    @Test
    fun exactlyOneSymbolRoundTrip() {
        runRoundTrip(
            size = 256
        )
    }

    @Test
    fun twoHundredFiftySevenByteFileRoundTrip() {
        runRoundTrip(
            size = 257
        )
    }

    @Test
    fun oneMiBFileRoundTrip() {
        runRoundTrip(
            size = 1024 * 1024
        )
    }

    @Test
    fun oneByteCreatesOnePaddedSymbol() {

        val file =
            byteArrayOf(
                0x5A
            )

        val blocks =
            Of8FileSymbolizer.split(
                file
            )

        assertEquals(
            1,
            blocks.size
        )

        assertEquals(
            1,
            blocks[0].k
        )

        assertEquals(
            0,
            blocks[0].symbols[0].esi
        )

        val symbol =
            blocks[0].symbols[0].bytes

        assertEquals(
            256,
            symbol.size
        )

        assertEquals(
            0x5A,
            symbol[0].toInt() and 0xFF
        )

        assertTrue(
            symbol
                .copyOfRange(1, 256)
                .all { it == 0.toByte() }
        )
    }

    @Test
    fun twoHundredFiftySevenBytesCreateTwoSymbols() {

        val file =
            createData(
                257
            )

        val blocks =
            Of8FileSymbolizer.split(
                file
            )

        assertEquals(
            1,
            blocks.size
        )

        assertEquals(
            2,
            blocks[0].k
        )

        assertEquals(
            0,
            blocks[0].symbols[0].esi
        )

        assertEquals(
            1,
            blocks[0].symbols[1].esi
        )
    }

    @Test
    fun sourceBlockStopsAt1024Symbols() {

        val size =
            ProtocolConstants
                .OF8_SOURCE_BLOCK_MAX_BYTES +
                    1

        val file =
            createData(size)

        val blocks =
            Of8FileSymbolizer.split(
                file
            )

        assertEquals(
            2,
            blocks.size
        )

        assertEquals(
            1024,
            blocks[0].k
        )

        assertEquals(
            1,
            blocks[1].k
        )

        assertEquals(
            ProtocolConstants
                .OF8_SOURCE_BLOCK_MAX_BYTES,
            blocks[0].originalByteCount
        )

        assertEquals(
            1,
            blocks[1].originalByteCount
        )
    }

    @Test
    fun oneMiBCreatesFourFullSourceBlocks() {

        val file =
            createData(
                1024 * 1024
            )

        val blocks =
            Of8FileSymbolizer.split(
                file
            )

        assertEquals(
            4,
            blocks.size
        )

        blocks.forEachIndexed { index, block ->

            assertEquals(
                index,
                block.blockNumber
            )

            assertEquals(
                1024,
                block.k
            )

            assertEquals(
                256 * 1024,
                block.originalByteCount
            )
        }
    }

    private fun runRoundTrip(
        size: Int
    ) {

        val original =
            createData(size)

        val originalHash =
            Sha256.hex(original)

        val blocks =
            Of8FileSymbolizer.split(
                original
            )

        val recovered =
            Of8FileSymbolizer.reconstruct(
                blocks = blocks,
                originalFileSize =
                    original.size
            )

        assertContentEquals(
            original,
            recovered
        )

        assertEquals(
            original.size,
            recovered.size
        )

        assertEquals(
            originalHash,
            Sha256.hex(recovered)
        )
    }

    private fun createData(
        size: Int
    ): ByteArray {

        return ByteArray(size) { index ->

            (
                    (
                            index.toLong() *
                                    131L +
                                    47L
                            ) and 0xFF
                    )
                .toByte()
        }
    }
}