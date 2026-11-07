package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class P1ControlBandLayoutTest {
    @Test
    fun controlCoordinatesAndPatternsMatchFrozenPc007() {
        assertEquals(P1ControlBandLayout.Rect(0, 40, 16, 16), P1ControlBandLayout.leftHeader)
        assertEquals(P1ControlBandLayout.Rect(144, 40, 16, 16), P1ControlBandLayout.rightHeader)
        assertEquals(P1ControlBandLayout.Rect(16, 2, 128, 1), P1ControlBandLayout.topTiming)
        assertEquals(P1ControlBandLayout.Rect(16, 93, 128, 1), P1ControlBandLayout.bottomTiming)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3), (0..7).map { P1ControlBandLayout.pilotLabel(it, 0) })
        assertEquals(listOf(4, 4, 5, 5, 6, 6, 7, 7), (0..7).map { P1ControlBandLayout.pilotLabel(it, 2) })
        assertEquals(0, P1ControlBandLayout.anchorLabel(0, 3))
        assertEquals(7, P1ControlBandLayout.anchorLabel(1, 1))
        assertEquals(0, P1ControlBandLayout.anchorLabel(2, 2))
    }

    @Test
    fun timingRecordRoundTripsSequenceAndDuplicatesAcrossRow() {
        for (sequence in listOf(0L, 1L, 0x1234L, 0xFFFFL, 0xFFFF_FFFFL)) {
            val encoded = P1TimingRecordCodec.encode(sequence)
            val decoded = P1TimingRecordCodec.decode(encoded)
            assertEquals(sequence.toInt() and 0xFFFF, decoded.frameSequenceLow16)
            assertEquals(sequence.toInt() and 1, decoded.parity)
            val row = P1TimingRecordCodec.encodeRepeatedRow(sequence)
            assertContentEquals(row.copyOfRange(0, 64), row.copyOfRange(64, 128))
            assertEquals(decoded, P1TimingRecordCodec.decodeRepeatedRow(row))
        }
    }

    @Test
    fun timingRecordRejectsCorruptionAndMismatchedCopies() {
        val corruptedRecord = P1TimingRecordCodec.encode(0xABCDEF01L)
        corruptedRecord[9] = !corruptedRecord[9]
        assertFailsWith<IllegalArgumentException> { P1TimingRecordCodec.decode(corruptedRecord) }

        val mismatchedRow = P1TimingRecordCodec.encodeRepeatedRow(77L)
        mismatchedRow[80] = !mismatchedRow[80]
        assertFailsWith<IllegalArgumentException> { P1TimingRecordCodec.decodeRepeatedRow(mismatchedRow) }
    }

    @Test
    fun physicalRendererPlacesAllApprovedControlStructures() {
        val header = OptiFrameGlobalHeader(
            profileId = ProtocolConstants.PROFILE_P1_ID,
            sessionId = 0x10203040L,
            frameSequence = 0x1234ABCDL,
            sourceBlockNumber = 2,
            paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
            frameType = ProtocolConstants.FRAME_TYPE_DATA,
            flags = 0,
            calibrationEpoch = 0
        )
        val frame = P1LogicalFrameBuilder.build(header, emptyList())
        val rendered = P1PhysicalFrameRenderer.render(frame)
        assertEquals(160, rendered.width)
        assertEquals(96, rendered.height)
        assertEquals(null, rendered.labelAt(16, 6)) // DATA_UNUSED remains empty.
        assertEquals(0, rendered.labelAt(20, 4)) // Unassigned controls default black.

        val matrix = OptiFrameGlobalHeaderMatrixCodec.encode(header)
        for (y in 0 until 16) for (x in 0 until 16) {
            val expected = if (matrix[y, x] == 0) 0 else 7
            assertEquals(expected, rendered.labelAt(x, y + 40))
            assertEquals(expected, rendered.labelAt(x + 144, y + 40))
        }

        val top = BooleanArray(128) { rendered.labelAt(16 + it, 2) == 7 }
        val bottom = BooleanArray(128) { rendered.labelAt(16 + it, 93) == 7 }
        assertEquals(P1TimingRecordCodec.decodeRepeatedRow(top), P1TimingRecordCodec.decodeRepeatedRow(bottom))
        assertEquals(0, rendered.labelAt(5, 5))
        assertEquals(7, rendered.labelAt(6, 6))
        assertEquals(0, rendered.labelAt(7, 7))
        assertEquals(P1ControlBandLayout.pilotLabel(6, 2), rendered.labelAt(10, 26))
        assertEquals(P1ControlBandLayout.pilotLabel(6, 2), rendered.labelAt(154, 26))

        val rgb = P1PhysicalFrameRasterizer.toRgb888(rendered)
        assertEquals(160 * 96 * 3, rgb.size)
        assertEquals(32, rgb[0].toInt() and 0xFF)
        assertEquals(32, rgb[1].toInt() and 0xFF)
        assertEquals(32, rgb[2].toInt() and 0xFF)
        val whiteAnchorCenterOffset = ((6 * 160 + 6) * 3)
        assertEquals(224, rgb[whiteAnchorCenterOffset].toInt() and 0xFF)
        assertEquals(224, rgb[whiteAnchorCenterOffset + 1].toInt() and 0xFF)
        assertEquals(224, rgb[whiteAnchorCenterOffset + 2].toInt() and 0xFF)
    }

    @Test
    fun rgbGridDecodeRecoversHeaderTimingAndOccupiedMacroblock() {
        val header = OptiFrameGlobalHeader(
            profileId = ProtocolConstants.PROFILE_P1_ID,
            sessionId = 0x10203040L,
            frameSequence = 0x1234ABCDL,
            sourceBlockNumber = 0,
            paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
            frameType = ProtocolConstants.FRAME_TYPE_DATA,
            flags = 0,
            calibrationEpoch = 0
        )
        val source = ByteArray(ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { (it * 17 + 3).toByte() }
        val position = P1SlotPosition.canonicalP1().first()
        val slot = P1LogicalFrameSlot.fromMacroblock(
            globalHeader = header,
            macroblock = Of8Macroblock(
                header = Of8MacroblockHeader(
                    esi = 0,
                    slotIndex = position.slotIndex,
                    flags = 0,
                    frameSeqLow = (header.frameSequence and 0xFFFF).toInt()
                ),
                payload = source
            ),
            macroblockColumn = position.macroblockColumn,
            macroblockRow = position.macroblockRow
        )
        val frame = P1LogicalFrameBuilder.build(header, listOf(slot))
        val pixels = P1PhysicalFrameRasterizer.toRgb888(
            P1PhysicalFrameRenderer.render(frame)
        )

        val decoded = P1Rgb888GridDecoder.decode(pixels)
        assertEquals(header, decoded.frame.header)
        for (row in 0 until P1PhysicalLayout.MACROBLOCK_HEIGHT) {
            for (column in 0 until P1PhysicalLayout.MACROBLOCK_WIDTH) {
                assertEquals(
                    slot.matrix[row, column],
                    decoded.frame.canvas.symbolAt(
                        P1PhysicalLayout.DATA_REGION_X + column,
                        P1PhysicalLayout.DATA_REGION_Y + row
                    )
                )
            }
        }
        assertEquals(null, decoded.frame.canvas.symbolAt(
            P1PhysicalLayout.DATA_REGION_X + P1PhysicalLayout.MACROBLOCK_WIDTH,
            P1PhysicalLayout.DATA_REGION_Y
        ))
        assertTrue(decoded.cellConfidence.all { it == 1.0 })
    }

    @Test
    fun rgbGridDecoderFallsBackToSecondHeaderAndRejectsCompositeTiming() {
        val header = OptiFrameGlobalHeader(
            profileId = ProtocolConstants.PROFILE_P1_ID,
            sessionId = 8,
            frameSequence = 0x10203040L,
            sourceBlockNumber = 0,
            paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
            frameType = ProtocolConstants.FRAME_TYPE_DATA,
            flags = 0,
            calibrationEpoch = 0
        )
        val frame = P1LogicalFrameBuilder.build(header, emptyList())
        val sourcePixels = P1PhysicalFrameRasterizer.toRgb888(P1PhysicalFrameRenderer.render(frame))

        val oneBadHeader = sourcePixels.copyOf()
        val leftTrailerCell = ((55 * P1PhysicalLayout.GRID_WIDTH + 15) * 3)
        val mixedColor = Of8Palette.symbolToColor(3)
        oneBadHeader[leftTrailerCell] = mixedColor.red.toByte()
        oneBadHeader[leftTrailerCell + 1] = mixedColor.green.toByte()
        oneBadHeader[leftTrailerCell + 2] = mixedColor.blue.toByte()
        assertEquals(header, P1Rgb888GridDecoder.decode(oneBadHeader).frame.header)

        val compositeTiming = sourcePixels.copyOf()
        val bottomTiming = P1ControlBandLayout.bottomTiming
        val differentFrameRow = P1TimingRecordCodec.encodeRepeatedRow(1)
        for (x in 0 until bottomTiming.width) {
            val color = Of8Palette.symbolToColor(if (differentFrameRow[x]) 7 else 0)
            val pixel = ((bottomTiming.y * P1PhysicalLayout.GRID_WIDTH + bottomTiming.x + x) * 3)
            compositeTiming[pixel] = color.red.toByte()
            compositeTiming[pixel + 1] = color.green.toByte()
            compositeTiming[pixel + 2] = color.blue.toByte()
        }
        assertFailsWith<P1CompositeFrameException> { P1Rgb888GridDecoder.decode(compositeTiming) }
    }

    @Test
    fun rgbGridDecoderCalibratesColorsFromPilotsAndMarksAmbiguousCellsLowConfidence() {
        val header = OptiFrameGlobalHeader(
            profileId = ProtocolConstants.PROFILE_P1_ID,
            sessionId = 19,
            frameSequence = 73,
            sourceBlockNumber = 0,
            paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
            frameType = ProtocolConstants.FRAME_TYPE_DATA,
            flags = 0,
            calibrationEpoch = 0
        )
        val source = ByteArray(ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { (it * 13 + 7).toByte() }
        val position = P1SlotPosition.canonicalP1().first()
        val slot = P1LogicalFrameSlot.fromMacroblock(
            globalHeader = header,
            macroblock = Of8Macroblock(
                header = Of8MacroblockHeader(
                    esi = 0,
                    slotIndex = position.slotIndex,
                    flags = 0,
                    frameSeqLow = (header.frameSequence and 0xFFFF).toInt()
                ),
                payload = source
            ),
            macroblockColumn = position.macroblockColumn,
            macroblockRow = position.macroblockRow
        )
        val logical = P1LogicalFrameBuilder.build(header, listOf(slot))
        val rendered = P1PhysicalFrameRasterizer.toRgb888(P1PhysicalFrameRenderer.render(logical))
        val shifted = ByteArray(rendered.size)
        for (offset in rendered.indices step 3) {
            val red = rendered[offset].toInt() and 0xFF
            val green = rendered[offset + 1].toInt() and 0xFF
            val blue = rendered[offset + 2].toInt() and 0xFF
            shifted[offset] = (0.75 * red + 0.10 * green + 18.0).toInt().toByte()
            shifted[offset + 1] = (0.08 * red + 0.75 * green + 22.0).toInt().toByte()
            shifted[offset + 2] = (0.05 * green + 0.75 * blue + 14.0).toInt().toByte()
        }

        val decoded = P1Rgb888GridDecoder.decode(shifted)
        assertEquals(header, decoded.frame.header)
        assertTrue(decoded.cellConfidence.all { it > 0.99 })
        for (row in 0 until P1PhysicalLayout.MACROBLOCK_HEIGHT) {
            for (column in 0 until P1PhysicalLayout.MACROBLOCK_WIDTH) {
                assertEquals(
                    slot.matrix[row, column],
                    decoded.frame.canvas.symbolAt(
                        P1PhysicalLayout.DATA_REGION_X + column,
                        P1PhysicalLayout.DATA_REGION_Y + row
                    )
                )
            }
        }

        val color0 = observedColor(Of8Palette.symbolToColor(0))
        val color1 = observedColor(Of8Palette.symbolToColor(1))
        val ambiguousOffset = (P1PhysicalLayout.DATA_REGION_Y * P1PhysicalLayout.GRID_WIDTH +
            P1PhysicalLayout.DATA_REGION_X) * P1PhysicalFrameRasterizer.BYTES_PER_PIXEL
        for (channel in 0..2) {
            shifted[ambiguousOffset + channel] = ((color0[channel] + color1[channel]) / 2).toByte()
        }
        val ambiguous = P1Rgb888GridDecoder.decode(shifted)
        val ambiguousCell = P1PhysicalLayout.DATA_REGION_Y * P1PhysicalLayout.GRID_WIDTH +
            P1PhysicalLayout.DATA_REGION_X
        assertTrue(ambiguous.cellConfidence[ambiguousCell] < 0.01)
    }

    private fun observedColor(color: RgbColor): IntArray {
        val red = color.red.toDouble()
        val green = color.green.toDouble()
        val blue = color.blue.toDouble()
        return intArrayOf(
            (0.75 * red + 0.10 * green + 18.0).toInt(),
            (0.08 * red + 0.75 * green + 22.0).toInt(),
            (0.05 * green + 0.75 * blue + 14.0).toInt()
        )
    }

    private fun assertContentEquals(expected: BooleanArray, actual: BooleanArray) {
        assertEquals(expected.size, actual.size)
        assertTrue(expected.indices.all { expected[it] == actual[it] })
    }
}
