package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class P1M3SessionTest {
    @Test
    fun safe30RoundTripVerifiesFileAndIgnoresRepeatedBlockAck() {
        val original = ByteArray(13 * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { index ->
            (index * 31).toByte()
        }
        val sessionId = 0x1234_5678L
        val sender = P1M3SenderSession.fromBytes(
            fileName = "sample.bin",
            fileBytes = original,
            initialRepairSymbolsPerBlock = 0,
            sessionId = sessionId,
            firstFrameSequence = 0xFFFF_FFFFL,
            encoderFactory = ::IdentitySourceEncoder,
            flagsForItem = { 0 },
            headerFactory = { blockNumber, frameSequence, kind ->
                OptiFrameGlobalHeader(
                    profileId = ProtocolConstants.PROFILE_P1_ID,
                    sessionId = sessionId,
                    frameSequence = frameSequence,
                    sourceBlockNumber = blockNumber,
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
        val receiver = P1M3ReceiverSession(
            manifest = sender.manifest,
            decoderFactory = RaptorQBlockDecoderFactory { _, sourceSymbolCount ->
                IdentitySourceDecoder(sourceSymbolCount)
            },
            expectedSessionId = sessionId
        )

        val firstPresentation = assertNotNull(sender.nextVsyncPresentation())
        val repeatedPresentation = assertNotNull(sender.nextVsyncPresentation())
        val wrappedSequencePresentation = assertNotNull(sender.nextVsyncPresentation())
        val repeatedWrappedSequencePresentation = assertNotNull(sender.nextVsyncPresentation())
        assertEquals(0, firstPresentation.repeatOrdinal)
        assertEquals(1, repeatedPresentation.repeatOrdinal)
        assertEquals(160, firstPresentation.physicalFrame.width)
        val physicalTopTiming = BooleanArray(128) {
            firstPresentation.physicalFrame.labelAt(16 + it, 2) == 7
        }
        assertEquals(firstPresentation.frameSequence and 0xFFFFL,
            P1TimingRecordCodec.decodeRepeatedRow(physicalTopTiming).frameSequenceLow16.toLong())
        assertEquals(0xFFFF_FFFFL, firstPresentation.frameSequence)
        assertEquals(0L, wrappedSequencePresentation.frameSequence)
        assertEquals(0, wrappedSequencePresentation.repeatOrdinal)
        assertEquals(1, repeatedWrappedSequencePresentation.repeatOrdinal)

        val firstReceive = receiver.acceptVsyncFrame(firstPresentation.frame)
        val repairRequest = assertNotNull(firstReceive.feedback)
        assertEquals(false, repairRequest.blockDecoded)
        assertEquals(2, repairRequest.additionalRepairSymbols)
        sender.applyFeedback(repairRequest)

        val repeatedReceive = receiver.acceptVsyncFrame(repeatedPresentation.frame)
        assertNull(repeatedReceive.feedback)

        val finalReceive = receiver.acceptVsyncFrame(wrappedSequencePresentation.frame)
        val completionAck = assertNotNull(finalReceive.feedback)
        sender.applyFeedback(completionAck)

        val repeatedFinalReceive = receiver.acceptVsyncFrame(repeatedWrappedSequencePresentation.frame)
        sender.applyFeedback(assertNotNull(repeatedFinalReceive.feedback))

        assertNull(sender.nextVsyncPresentation())
        assertTrue(sender.progress.isComplete)
        assertContentEquals(original, receiver.finishAndVerifyFile())

        val metrics = receiver.metrics
        assertTrue(metrics.sha256Verified)
        assertEquals(original.size.toLong(), assertNotNull(metrics.verifiedFileBytes))
        assertEquals(2L, metrics.uniqueLogicalFrames)
        assertEquals(2L, metrics.duplicateCaptures)
        assertEquals(1.0, assertNotNull(metrics.macroblockPassRate))
        assertEquals(13L, metrics.uniqueSymbolsReceived)
    }

    @Test
    fun receiverRejectsFramesFromAnotherSession() {
        val sender = P1M3SenderSession.fromBytes(
            fileName = "sample.bin",
            fileBytes = byteArrayOf(1),
            initialRepairSymbolsPerBlock = 0,
            sessionId = 7,
            firstFrameSequence = 0,
            encoderFactory = ::IdentitySourceEncoder,
            flagsForItem = { 0 },
            headerFactory = { blockNumber, frameSequence, _ ->
                OptiFrameGlobalHeader(
                    profileId = ProtocolConstants.PROFILE_P1_ID,
                    sessionId = 7,
                    frameSequence = frameSequence,
                    sourceBlockNumber = blockNumber,
                    paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
                    frameType = ProtocolConstants.FRAME_TYPE_DATA,
                    flags = 0,
                    calibrationEpoch = 0
                )
            }
        )
        val receiver = P1M3ReceiverSession(
            manifest = sender.manifest,
            decoderFactory = RaptorQBlockDecoderFactory { _, sourceSymbolCount ->
                IdentitySourceDecoder(sourceSymbolCount)
            },
            expectedSessionId = 8
        )
        val frame = assertNotNull(sender.nextVsyncPresentation()).frame

        assertFailsWith<IllegalArgumentException> {
            receiver.acceptVsyncFrame(frame)
        }
    }

    @Test
    fun fullyErasedOpticalFrameStillRequestsRepairsForItsSourceBlock() {
        val sessionId = 99L
        val sender = P1M3SenderSession.fromBytes(
            fileName = "one-byte.bin",
            fileBytes = byteArrayOf(0x5A),
            initialRepairSymbolsPerBlock = 0,
            sessionId = sessionId,
            firstFrameSequence = 4,
            encoderFactory = ::IdentitySourceEncoder,
            flagsForItem = { 0 },
            headerFactory = { blockNumber, frameSequence, _ ->
                OptiFrameGlobalHeader(
                    profileId = ProtocolConstants.PROFILE_P1_ID,
                    sessionId = sessionId,
                    frameSequence = frameSequence,
                    sourceBlockNumber = blockNumber,
                    paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
                    frameType = ProtocolConstants.FRAME_TYPE_DATA,
                    flags = 0,
                    calibrationEpoch = 0
                )
            }
        )
        val receiver = P1M3ReceiverSession(
            manifest = sender.manifest,
            decoderFactory = RaptorQBlockDecoderFactory { _, sourceSymbolCount ->
                IdentitySourceDecoder(sourceSymbolCount)
            },
            expectedSessionId = sessionId
        )
        val validFrame = assertNotNull(sender.nextVsyncPresentation()).frame
        val fullyErasedFrame = P1LogicalFrameBuilder.build(validFrame.header, emptyList())

        val result = receiver.acceptVsyncFrame(fullyErasedFrame)

        assertEquals(ProtocolConstants.P1_MACROBLOCK_COUNT, result.slotFailures.size)
        assertEquals(0, assertNotNull(result.feedback).uniqueSymbolsReceived)
        assertEquals(2, assertNotNull(result.feedback).additionalRepairSymbols)
    }

    @Test
    fun m4RequestsRepairsWhenAWholeOpticalFrameWasSkipped() {
        val sessionId = 101L
        val sender = P1M3SenderSession.fromBytes(
            fileName = "frame-gap.bin",
            fileBytes = ByteArray(30 * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { it.toByte() },
            initialRepairSymbolsPerBlock = 0,
            sessionId = sessionId,
            firstFrameSequence = 0,
            encoderFactory = ::IdentitySourceEncoder,
            flagsForItem = { 0 },
            headerFactory = { blockNumber, frameSequence, kind ->
                OptiFrameGlobalHeader(
                    profileId = ProtocolConstants.PROFILE_P1_ID,
                    sessionId = sessionId,
                    frameSequence = frameSequence,
                    sourceBlockNumber = blockNumber,
                    paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
                    frameType = if (kind == RaptorQSymbol.Kind.SOURCE)
                        ProtocolConstants.FRAME_TYPE_DATA else ProtocolConstants.FRAME_TYPE_REPAIR,
                    flags = 0,
                    calibrationEpoch = 0
                )
            },
            fast60 = true
        )
        val receiver = P1M3ReceiverSession(
            manifest = sender.manifest,
            decoderFactory = RaptorQBlockDecoderFactory { _, sourceSymbolCount ->
                IdentitySourceDecoder(sourceSymbolCount)
            },
            expectedSessionId = sessionId
        )
        val first = assertNotNull(sender.nextVsyncPresentation()).frame
        sender.nextVsyncPresentation() // Frame 1 is intentionally lost.
        val third = assertNotNull(sender.nextVsyncPresentation()).frame

        assertNull(receiver.acceptVsyncFrame(first, requestRepairsOnlyOnSlotFailures = true).feedback)
        val gapFeedback = receiver.acceptVsyncFrame(third, requestRepairsOnlyOnSlotFailures = true).feedback
        assertEquals(2, assertNotNull(gapFeedback).additionalRepairSymbols)
    }

    private class IdentitySourceEncoder(
        private val sourceBlock: Of8SourceBlock
    ) : RaptorQBlockEncoder {
        override val sourceSymbolCount: Int
            get() = sourceBlock.k

        override fun encodeSymbol(esi: Long): RaptorQSymbol {
            require(esi < sourceSymbolCount)
            val source = sourceBlock.symbols[esi.toInt()]
            return RaptorQSymbol(
                esi = esi,
                bytes = source.bytes,
                kind = RaptorQSymbol.Kind.SOURCE
            )
        }
    }

    private class IdentitySourceDecoder(
        override val sourceSymbolCount: Int
    ) : RaptorQBlockDecoder {
        private val sourceSymbols = arrayOfNulls<ByteArray>(sourceSymbolCount)

        override fun addSymbol(symbol: RaptorQSymbol): Boolean {
            if (symbol.esi >= sourceSymbolCount) return false
            val index = symbol.esi.toInt()
            if (sourceSymbols[index] != null) return false
            sourceSymbols[index] = symbol.bytes.copyOf()
            return true
        }

        override fun decodeSourceSymbols(): List<ByteArray>? {
            if (sourceSymbols.any { it == null }) return null
            return sourceSymbols.map { checkNotNull(it).copyOf() }
        }
    }
}
