package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File

class OpenRqBlockAdapterTest {
    @Test
    fun fileBackedSafe30SessionReadsBlocksOnDemandAndVerifiesStreamedOutput() {
        val original = ByteArray(13 * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES + 37) { index ->
            (index * 19 + 7).toByte()
        }
        val file = File.createTempFile("optiframe-m3", ".bin")
        try {
            file.writeBytes(original)
            val sessionId = 0x1234ABCDL
            val sender = OpenRqRaptorQ.m3SenderSessionFromFile(
                fileName = "file-backed.bin",
                file = file,
                initialRepairSymbolsPerBlock = 0,
                initialRepairFraction = 0.20,
                advanceAfterRepairQueue = true,
                sessionId = sessionId,
                firstFrameSequence = 0,
                flagsForItem = { 0 },
                headerFactory = { block, sequence, kind ->
                    OptiFrameGlobalHeader(
                        profileId = ProtocolConstants.PROFILE_P1_ID,
                        sessionId = sessionId,
                        frameSequence = sequence,
                        sourceBlockNumber = block,
                        paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
                        frameType = if (kind == RaptorQSymbol.Kind.SOURCE)
                            ProtocolConstants.FRAME_TYPE_DATA else ProtocolConstants.FRAME_TYPE_REPAIR,
                        flags = 0,
                        calibrationEpoch = 0
                    )
                }
            )
            val receiver = OpenRqRaptorQ.m3ReceiverSession(sender.manifest, sessionId)
            while (true) {
                val presentation = sender.nextVsyncPresentation() ?: break
                if (presentation.repeatOrdinal == 0) receiver.acceptVsyncFrame(presentation.frame)
                if (presentation.repeatOrdinal == 0) assertNotNull(sender.nextVsyncPresentation())
            }
            assertTrue(sender.progress.isComplete)
            val output = java.io.ByteArrayOutputStream()
            receiver.finishAndVerifyFile(output)
            assertContentEquals(original, output.toByteArray())
            assertTrue(receiver.metrics.sha256Verified)
        } finally {
            file.delete()
        }
    }

    @Test
    fun repairSymbolRecoversOneMissingSourceSymbol() {
        val original = ByteArray(13 * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { index ->
            (index * 73 + 11).toByte()
        }
        val sourceBlock = Of8FileSymbolizer.split(original).single()
        val encoder = OpenRqBlockEncoder(sourceBlock)
        val decoder = OpenRqBlockDecoder(sourceBlock.k)

        for (esi in 1 until sourceBlock.k) {
            assertTrue(decoder.addSymbol(encoder.encodeSymbol(esi.toLong())))
        }
        assertTrue(decoder.addSymbol(encoder.encodeSymbol(sourceBlock.k.toLong())))

        val recovered = assertNotNull(decoder.decodeSourceSymbols())
        sourceBlock.symbols.indices.forEach { index ->
            assertContentEquals(sourceBlock.symbols[index].bytes, recovered[index])
        }
    }

    @Test
    fun realOpenRqP1Safe30SessionRepairsAnErasedSlotAndVerifiesFile() {
        val original = ByteArray(13 * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { index ->
            (index * 41 + 29).toByte()
        }
        val sessionId = 0x76543210L
        val sender = OpenRqRaptorQ.m3SenderSession(
            fileName = "openrq-e2e.bin",
            fileBytes = original,
            initialRepairSymbolsPerBlock = 1,
            sessionId = sessionId,
            firstFrameSequence = 0xFFFF_FFFFL,
            flagsForItem = { 0 },
            headerFactory = { block, sequence, kind ->
                OptiFrameGlobalHeader(
                    profileId = ProtocolConstants.PROFILE_P1_ID,
                    sessionId = sessionId,
                    frameSequence = sequence,
                    sourceBlockNumber = block,
                    paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
                    frameType = when (kind) {
                        RaptorQSymbol.Kind.SOURCE -> ProtocolConstants.FRAME_TYPE_DATA
                        RaptorQSymbol.Kind.REPAIR -> ProtocolConstants.FRAME_TYPE_REPAIR
                    },
                    flags = 0,
                    calibrationEpoch = 0
                )
            }
        )
        val receiver = OpenRqRaptorQ.m3ReceiverSession(
            manifest = sender.manifest,
            expectedSessionId = sessionId
        )
        var firstUniqueFrame = true
        while (!sender.progress.isComplete) {
            val presentation = assertNotNull(sender.nextVsyncPresentation())
            val firstDecoded = decodeCapture(presentation.frame, eraseFirstSlot = firstUniqueFrame)
            val firstCapture = receiver.acceptVsyncFrame(
                frame = firstDecoded.frame,
                fullGridConfidence = firstDecoded.cellConfidence,
                erasureThreshold = 0.5
            )
            val repeated = assertNotNull(sender.nextVsyncPresentation())
            assertEquals(presentation.frameSequence, repeated.frameSequence)
            assertEquals(1, repeated.repeatOrdinal)
            val repeatedDecoded = decodeCapture(repeated.frame, eraseFirstSlot = firstUniqueFrame)
            val secondCapture = receiver.acceptVsyncFrame(
                frame = repeatedDecoded.frame,
                fullGridConfidence = repeatedDecoded.cellConfidence,
                erasureThreshold = 0.5
            )
            sender.applyFeedback(firstCapture.feedback ?: assertNotNull(secondCapture.feedback))
            firstUniqueFrame = false
        }

        assertNull(sender.nextVsyncPresentation())
        assertContentEquals(original, receiver.finishAndVerifyFile())
        assertTrue(receiver.metrics.sha256Verified)
        assertEquals(original.size.toLong(), receiver.metrics.verifiedFileBytes)
    }

    @Test
    fun fixedRepairBudgetCompletesMultiBlockSafe30WithoutFeedback() {
        val original = ByteArray(
            ProtocolConstants.OF8_SOURCE_BLOCK_MAX_BYTES +
                ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
        ) { index -> (index * 29 + 17).toByte() }
        val sessionId = 0x10293847L
        val sender = OpenRqRaptorQ.m3SenderSession(
            fileName = "fixed-repair.bin",
            fileBytes = original,
            initialRepairSymbolsPerBlock = 0,
            initialRepairFraction = 0.20,
            advanceAfterRepairQueue = true,
            sessionId = sessionId,
            firstFrameSequence = 0,
            flagsForItem = { 0 },
            headerFactory = { block, sequence, kind ->
                OptiFrameGlobalHeader(
                    profileId = ProtocolConstants.PROFILE_P1_ID,
                    sessionId = sessionId,
                    frameSequence = sequence,
                    sourceBlockNumber = block,
                    paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
                    frameType = if (kind == RaptorQSymbol.Kind.SOURCE)
                        ProtocolConstants.FRAME_TYPE_DATA else ProtocolConstants.FRAME_TYPE_REPAIR,
                    flags = 0,
                    calibrationEpoch = 0
                )
            }
        )
        val receiver = OpenRqRaptorQ.m3ReceiverSession(sender.manifest, sessionId)

        while (true) {
            val first = sender.nextVsyncPresentation() ?: break
            if (first.repeatOrdinal == 0) receiver.acceptVsyncFrame(first.frame)
            val repeated = assertNotNull(sender.nextVsyncPresentation())
            assertEquals(first.frameSequence, repeated.frameSequence)
            assertEquals(1, repeated.repeatOrdinal)
        }

        assertNull(sender.nextVsyncPresentation())
        assertTrue(sender.progress.isComplete)
        assertTrue(receiver.progress.isComplete)
        assertContentEquals(original, receiver.finishAndVerifyFile())
        assertTrue(receiver.metrics.sha256Verified)
    }

    private fun decodeCapture(
        frame: P1LogicalOpticalFrame,
        eraseFirstSlot: Boolean
    ): P1Rgb888GridDecoder.DecodedGrid {
        val rendered = P1PhysicalFrameRenderer.render(frame)
        val pixels = P1PhysicalFrameRasterizer.toRgb888(rendered)
        if (!eraseFirstSlot) return P1Rgb888GridDecoder.decode(pixels)

        val position = P1SlotPosition.canonicalP1().first()
        val originX = P1PhysicalLayout.DATA_REGION_X +
            position.macroblockColumn * P1PhysicalLayout.MACROBLOCK_WIDTH
        val originY = P1PhysicalLayout.DATA_REGION_Y +
            position.macroblockRow * P1PhysicalLayout.MACROBLOCK_HEIGHT
        for (y in 0 until P1PhysicalLayout.MACROBLOCK_HEIGHT) {
            for (x in 0 until P1PhysicalLayout.MACROBLOCK_WIDTH) {
                val offset = ((originY + y) * P1PhysicalLayout.GRID_WIDTH + originX + x) * 3
                pixels[offset] = 0x80.toByte()
                pixels[offset + 1] = 0x80.toByte()
                pixels[offset + 2] = 0x80.toByte()
            }
        }
        return P1Rgb888GridDecoder.decode(pixels)
    }
}
