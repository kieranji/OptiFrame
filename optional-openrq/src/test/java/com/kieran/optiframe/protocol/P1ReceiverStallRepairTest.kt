package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class P1ReceiverStallRepairTest {
    @Test
    fun fast60TrailingFrameLossRecoversWithWatchdogRepairRequest() {
        val original = ByteArray(13 * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { index ->
            (index * 53 + 17).toByte()
        }
        val sessionId = 0x0A0B0C0DL
        val sender = OpenRqRaptorQ.m3SenderSession(
            fileName = "fast60-stall.bin",
            fileBytes = original,
            initialRepairSymbolsPerBlock = 0,
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
            },
            fast60 = true
        )
        val receiver = OpenRqRaptorQ.m3ReceiverSession(sender.manifest, sessionId)
        val watchdog = P1ReceiverStallWatchdog()

        val firstFrame = assertNotNull(sender.nextVsyncPresentation()).frame
        val firstResult = receiver.acceptVsyncFrame(
            frame = firstFrame,
            requestRepairsOnlyOnSlotFailures = true
        )
        assertNull(firstResult.feedback)
        watchdog.onFrame(firstFrame.header.sourceBlockNumber, firstResult, 0L)

        var droppedFrames = 0
        while (sender.nextVsyncPresentation() != null) droppedFrames++
        assertTrue(droppedFrames > 0)
        assertFalse(receiver.isComplete)

        val stalledBlock = assertNotNull(
            watchdog.poll(P1ReceiverStallWatchdog.DEFAULT_STALL_TIMEOUT_NANOS)
        )
        assertEquals(0, stalledBlock)
        val repairFeedback = assertNotNull(receiver.stallRepairFeedback(stalledBlock))
        assertFalse(repairFeedback.blockDecoded)
        assertEquals(3, repairFeedback.additionalRepairSymbols)
        assertEquals(12, repairFeedback.uniqueSymbolsReceived)
        sender.applyControlMessage(
            P1ControlMessage.RepairRequested(
                sessionId = sessionId,
                sourceBlockNumber = repairFeedback.sourceBlockNumber,
                additionalRepairSymbols = repairFeedback.additionalRepairSymbols,
                uniqueSymbolsReceived = repairFeedback.uniqueSymbolsReceived
            )
        )

        var decodedFeedback = false
        while (!receiver.isComplete) {
            val presentation = assertNotNull(sender.nextVsyncPresentation())
            val result = receiver.acceptVsyncFrame(
                frame = presentation.frame,
                requestRepairsOnlyOnSlotFailures = true
            )
            result.feedback?.let { feedback ->
                sender.applyControlMessage(feedback.toControlMessage(sessionId))
                decodedFeedback = feedback.blockDecoded
            }
        }

        assertTrue(decodedFeedback)
        assertTrue(sender.progress.isComplete)
        assertNull(sender.nextVsyncPresentation())
        assertContentEquals(original, receiver.finishAndVerifyFile())
        assertTrue(receiver.metrics.sha256Verified)
    }

    private fun RaptorQBlockFeedback.toControlMessage(sessionId: Long): P1ControlMessage =
        if (blockDecoded) {
            P1ControlMessage.BlockDecoded(
                sessionId = sessionId,
                sourceBlockNumber = sourceBlockNumber,
                uniqueSymbolsReceived = uniqueSymbolsReceived
            )
        } else {
            P1ControlMessage.RepairRequested(
                sessionId = sessionId,
                sourceBlockNumber = sourceBlockNumber,
                additionalRepairSymbols = additionalRepairSymbols,
                uniqueSymbolsReceived = uniqueSymbolsReceived
            )
        }
}
