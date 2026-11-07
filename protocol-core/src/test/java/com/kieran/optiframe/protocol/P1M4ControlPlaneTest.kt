package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class P1M4ControlPlaneTest {
    @Test
    fun controlMessagesRoundTripAndRejectCorruption() {
        val messages = listOf(
            P1ControlMessage.BlockDecoded(0x12345678, 0xFFFF, 1024),
            P1ControlMessage.RepairRequested(0x12345678, 4, 9, 733),
            P1ControlMessage.RepairRequested(0x12345678, 0xFFFF, 9, 733),
            P1ControlMessage.Command(0x12345678, P1ControlMessage.Action.PAUSE),
            P1ControlMessage.Command(0x12345678, P1ControlMessage.Action.RESUME),
            P1ControlMessage.Command(0x12345678, P1ControlMessage.Action.CANCEL),
            P1ControlMessage.Command(0x12345678, P1ControlMessage.Action.TRANSFER_COMPLETE)
        )
        messages.forEach { assertEquals(it, P1ControlMessageCodec.decode(P1ControlMessageCodec.encode(it))) }

        val corrupted = P1ControlMessageCodec.encode(messages.first()).also { it[8] = 0 }
        assertFailsWith<IllegalArgumentException> { P1ControlMessageCodec.decode(corrupted) }
    }

    @Test
    fun fast60AdvancesUniqueFramesOnEachPresentation() {
        val sessionId = 0x0A0B0C0DL
        val sender = P1M3SenderSession.fromBytes(
            fileName = "fast60.bin",
            fileBytes = ByteArray(13 * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { it.toByte() },
            initialRepairSymbolsPerBlock = 0,
            sessionId = sessionId,
            firstFrameSequence = 900,
            encoderFactory = { block -> IdentityEncoder(block) },
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

        val first = assertNotNull(sender.nextVsyncPresentation())
        val second = assertNotNull(sender.nextVsyncPresentation())
        assertEquals(true, sender.fast60)
        assertEquals(900, first.frameSequence)
        assertEquals(901, second.frameSequence)
        assertEquals(0, first.repeatOrdinal)
        assertEquals(0, second.repeatOrdinal)
    }

    @Test
    fun controlCommandsPauseResumeAndValidateSession() {
        val sessionId = 45L
        val sender = P1M3SenderSession.fromBytes(
            fileName = "control.bin",
            fileBytes = byteArrayOf(1),
            initialRepairSymbolsPerBlock = 0,
            sessionId = sessionId,
            firstFrameSequence = 0,
            encoderFactory = { block -> IdentityEncoder(block) },
            flagsForItem = { 0 },
            headerFactory = { block, sequence, kind ->
                OptiFrameGlobalHeader(
                    ProtocolConstants.PROFILE_P1_ID, sessionId, sequence, block,
                    ProtocolConstants.PALETTE_OF8_FIXED,
                    if (kind == RaptorQSymbol.Kind.SOURCE) ProtocolConstants.FRAME_TYPE_DATA
                    else ProtocolConstants.FRAME_TYPE_REPAIR,
                    0, 0
                )
            },
            fast60 = true
        )
        sender.applyControlMessage(P1ControlMessage.Command(sessionId, P1ControlMessage.Action.PAUSE))
        assertEquals(true, sender.isPaused)
        assertNull(sender.nextVsyncPresentation())
        sender.applyControlMessage(P1ControlMessage.Command(sessionId, P1ControlMessage.Action.RESUME))
        assertEquals(false, sender.isPaused)
        assertNotNull(sender.nextVsyncPresentation())
        assertFailsWith<IllegalArgumentException> {
            sender.applyControlMessage(P1ControlMessage.Command(44, P1ControlMessage.Action.PAUSE))
        }
    }

    private class IdentityEncoder(private val block: Of8SourceBlock) : RaptorQBlockEncoder {
        override val sourceSymbolCount: Int get() = block.k
        override fun encodeSymbol(esi: Long): RaptorQSymbol {
            val source = block.symbols[esi.toInt()]
            return RaptorQSymbol(esi, source.bytes, RaptorQSymbol.Kind.SOURCE)
        }
    }
}
