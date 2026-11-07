package com.kieran.optiframe.protocol

import java.io.OutputStream

/** High-level M3 receiver for P1 slot decoding and RaptorQ file recovery. */
class P1M3ReceiverSession(
    manifest: OptiFrameManifest,
    decoderFactory: RaptorQBlockDecoderFactory,
    private val expectedSessionId: Long,
    positions: List<P1SlotPosition> = P1SlotPosition.canonicalP1()
) {
    private val fileReceiver = RaptorQFileReceiverSession(manifest, decoderFactory)
    private val slotPositions = positions.toList()
    private val receivedFrameSequences = HashSet<Long>()
    private var lastFrameSequence: Long? = null
    private val expectedFileSizeBytes = manifest.fileSize
    private var opticalFramesCaptured = 0L
    private var macroblocksAttempted = 0L
    private var macroblocksDecoded = 0L
    private var macroblocksFailed = 0L
    private var transferStartedAtNanos: Long? = null
    private var verifiedAtNanos: Long? = null

    init {
        require(expectedSessionId in 0..0xFFFF_FFFFL) {
            "expectedSessionId must fit uint32"
        }
        require(slotPositions.isNotEmpty()) { "At least one P1 slot position is required" }
        require(slotPositions.size <= ProtocolConstants.P1_MACROBLOCK_COUNT)
        P1DataRegionExtractor.validatePositions(slotPositions)
    }

    val progress: RaptorQFileReceiveProgress
        get() = fileReceiver.progress

    val isComplete: Boolean
        get() = fileReceiver.isComplete

    /** Snapshot used for the M3 goodput and macroblock pass-rate report. */
    val metrics: P1M3TransferMetrics
        get() {
            val start = transferStartedAtNanos
            val endOrNow = verifiedAtNanos ?: start?.let { System.nanoTime() }
            val elapsedMillis = if (start == null || endOrNow == null) null
            else ((endOrNow - start).coerceAtLeast(0L) / 1_000_000L)
            val uniqueFrames = receivedFrameSequences.size.toLong()
            val duplicateCaptures = opticalFramesCaptured - uniqueFrames
            val uniqueSymbols = progress.receivedSymbolsPerBlock.sumOf { it.toLong() }
            val goodput = if (verifiedAtNanos != null && elapsedMillis != null && elapsedMillis > 0L) {
                expectedFileSizeBytes.toDouble() / 1024.0 / (elapsedMillis / 1000.0)
            } else {
                null
            }

            return P1M3TransferMetrics(
                opticalFramesCaptured = opticalFramesCaptured,
                uniqueLogicalFrames = uniqueFrames,
                duplicateCaptures = duplicateCaptures,
                macroblocksAttempted = macroblocksAttempted,
                macroblocksDecoded = macroblocksDecoded,
                macroblocksFailed = macroblocksFailed,
                uniqueSymbolsReceived = uniqueSymbols,
                sha256Verified = verifiedAtNanos != null,
                elapsedMillis = elapsedMillis,
                verifiedFileBytes = if (verifiedAtNanos != null) expectedFileSizeBytes else null,
                goodputKibPerSecond = goodput
            )
        }

    fun acceptVsyncFrame(
        frame: P1LogicalOpticalFrame,
        fullGridConfidence: DoubleArray? = null,
        erasureThreshold: Double = 0.0,
        additionalRepairSymbolsIfIncomplete: Int = DEFAULT_ADDITIONAL_REPAIR_SYMBOLS,
        requestRepairsOnlyOnSlotFailures: Boolean = false
    ): RaptorQOpticalFrameReceiveResult {
        require(frame.header.sessionId == expectedSessionId) {
            "Optical frame session ID does not match this receiver session"
        }
        if (transferStartedAtNanos == null) transferStartedAtNanos = System.nanoTime()
        opticalFramesCaptured++
        val previousSequence = lastFrameSequence
        val expectedSequence = previousSequence?.let {
            if (it == P1Safe30FrameScheduler.MAX_FRAME_SEQUENCE) 0L else it + 1L
        } ?: 0L
        val frameSequenceGap = frame.header.frameSequence != expectedSequence &&
            (previousSequence == null || frame.header.frameSequence != previousSequence)
        lastFrameSequence = frame.header.frameSequence
        receivedFrameSequences += frame.header.frameSequence
        val received = fileReceiver.decodeAndAcceptOpticalFrame(
            frame = frame,
            positions = slotPositions,
            fullGridConfidence = fullGridConfidence,
            erasureThreshold = erasureThreshold,
            additionalRepairSymbolsIfIncomplete = additionalRepairSymbolsIfIncomplete,
            // A sequence gap means complete optical frames were lost even if
            // all slots in this capture decoded cleanly.
            requestRepairsOnlyOnSlotFailures = requestRepairsOnlyOnSlotFailures && !frameSequenceGap
        )
        val decodedSlots = received.symbolResults.size.toLong()
        // Empty DATA_UNUSED rectangles are still surfaced as failures by the
        // low-level decoder so an all-erasure frame can request repairs. For
        // optical quality metrics, count only failures with observed OF8 cells;
        // otherwise a short final frame is penalized for unused capacity.
        val failedSlots = received.slotFailures.count { failure ->
            val position = slotPositions.firstOrNull { it.slotIndex == failure.slotIndex }
            position != null && hasAnyCell(frame, position)
        }.toLong()
        macroblocksDecoded += decodedSlots
        macroblocksFailed += failedSlots
        macroblocksAttempted += decodedSlots + failedSlots
        return received
    }

    private fun hasAnyCell(frame: P1LogicalOpticalFrame, position: P1SlotPosition): Boolean {
        val originX = P1PhysicalLayout.DATA_REGION_X +
            position.macroblockColumn * P1PhysicalLayout.MACROBLOCK_WIDTH
        val originY = P1PhysicalLayout.DATA_REGION_Y +
            position.macroblockRow * P1PhysicalLayout.MACROBLOCK_HEIGHT
        for (row in 0 until P1PhysicalLayout.MACROBLOCK_HEIGHT) {
            for (column in 0 until P1PhysicalLayout.MACROBLOCK_WIDTH) {
                if (frame.canvas.symbolAt(originX + column, originY + row) != null) return true
            }
        }
        return false
    }

    fun finishAndVerifyFile(): ByteArray {
        val bytes = fileReceiver.finishAndVerify()
        if (verifiedAtNanos == null) verifiedAtNanos = System.nanoTime()
        return bytes
    }

    fun finishAndVerifyFile(output: OutputStream) {
        fileReceiver.finishAndVerify(output)
        if (verifiedAtNanos == null) verifiedAtNanos = System.nanoTime()
    }

    companion object {
        /** Two repairs per newly observed incomplete logical frame (~16.7% for 12 full P1 slots). */
        const val DEFAULT_ADDITIONAL_REPAIR_SYMBOLS: Int = 2
    }
}
