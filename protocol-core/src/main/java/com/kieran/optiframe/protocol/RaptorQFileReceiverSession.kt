package com.kieran.optiframe.protocol

import java.io.File
import java.io.OutputStream

/** File-level receiver setup using the supplied block decoder factory. */
class RaptorQFileReceiverSession(
    manifest: OptiFrameManifest,
    decoderFactory: RaptorQBlockDecoderFactory,
    spoolFile: File? = null
) {
    private val lastRepairRequestFrameSequenceByBlock = HashMap<Int, Long>()
    private val receiver = RaptorQFileReceiver(
        manifest = manifest,
        decoderFactory = decoderFactory,
        spoolFile = spoolFile
    )

    val isComplete: Boolean
        get() = receiver.isComplete

    val progress: RaptorQFileReceiveProgress
        get() = receiver.progress

    fun accept(item: RaptorQTransmissionItem): RaptorQBlockReceiveResult =
        receiver.accept(item)

    fun sourceSymbolCount(sourceBlockNumber: Int): Int =
        receiver.sourceSymbolCount(sourceBlockNumber)

    fun uniqueSymbolsReceived(sourceBlockNumber: Int): Int =
        receiver.uniqueSymbolsReceived(sourceBlockNumber)

    fun acceptAndFeedback(
        item: RaptorQTransmissionItem,
        additionalRepairSymbolsIfIncomplete: Int = 0
    ): RaptorQFileReceiveEvent {
        require(additionalRepairSymbolsIfIncomplete >= 0)
        val result = receiver.accept(item)
        val blockNumber = item.sourceBlockNumber
        val completed = receiver.isBlockComplete(blockNumber)
        val feedback = when {
            completed -> feedbackFor(blockNumber, 0)
            result.disposition != RaptorQBlockReceiveResult.Disposition.ACCEPTED &&
                result.disposition != RaptorQBlockReceiveResult.Disposition.BLOCK_COMPLETE -> null
            additionalRepairSymbolsIfIncomplete > 0 -> feedbackFor(
                blockNumber,
                additionalRepairSymbolsIfIncomplete
            )
            else -> null
        }
        return RaptorQFileReceiveEvent(result, feedback)
    }

    fun acceptOpticalFrame(
        frame: P1UnpackedRaptorQFrame,
        additionalRepairSymbolsIfIncomplete: Int = 0,
        requestRepairsOnlyOnSlotFailures: Boolean = false
    ): RaptorQOpticalFrameReceiveResult {
        require(additionalRepairSymbolsIfIncomplete >= 0)
        require(frame.transmissionItems.all {
            it.sourceBlockNumber == frame.sourceBlockNumber
        }) {
            "An optical P1 frame must not mix source blocks"
        }
        val results = frame.transmissionItems.map(receiver::accept)
        val blockNumber = frame.sourceBlockNumber
        val madeProgress = results.any {
            it.disposition == RaptorQBlockReceiveResult.Disposition.ACCEPTED ||
                it.disposition == RaptorQBlockReceiveResult.Disposition.BLOCK_COMPLETE
        }
        val completed = receiver.isBlockComplete(blockNumber)
        val hasSlotFailures = frame.slotFailures.isNotEmpty()
        val alreadyRequestedForFrame =
            lastRepairRequestFrameSequenceByBlock[blockNumber] == frame.frameSequence
        val feedback = if (completed) {
            feedbackFor(blockNumber, 0)
        } else if (additionalRepairSymbolsIfIncomplete > 0 &&
            (madeProgress || hasSlotFailures) &&
            (!requestRepairsOnlyOnSlotFailures || hasSlotFailures) &&
            !alreadyRequestedForFrame
        ) {
            lastRepairRequestFrameSequenceByBlock[blockNumber] = frame.frameSequence
            feedbackFor(blockNumber, additionalRepairSymbolsIfIncomplete)
        } else {
            null
        }
        return RaptorQOpticalFrameReceiveResult(
            symbolResults = results,
            slotFailures = frame.slotFailures,
            feedback = feedback
        )
    }

    fun decodeAndAcceptOpticalFrame(
        frame: P1LogicalOpticalFrame,
        positions: List<P1SlotPosition>,
        fullGridConfidence: DoubleArray? = null,
        erasureThreshold: Double = 0.0,
        additionalRepairSymbolsIfIncomplete: Int = 0,
        requestRepairsOnlyOnSlotFailures: Boolean = false
    ): RaptorQOpticalFrameReceiveResult {
        val unpacked = P1RaptorQFrameCodec.unpack(
            frame = frame,
            positions = positions,
            sourceSymbolCount = receiver.sourceSymbolCount(frame.header.sourceBlockNumber),
            fullGridConfidence = fullGridConfidence,
            erasureThreshold = erasureThreshold
        )
        return acceptOpticalFrame(
            frame = unpacked,
            additionalRepairSymbolsIfIncomplete = additionalRepairSymbolsIfIncomplete,
            requestRepairsOnlyOnSlotFailures = requestRepairsOnlyOnSlotFailures
        )
    }

    fun finishAndVerify(): ByteArray = receiver.finishAndVerify()

    fun finishAndVerify(output: OutputStream) = receiver.finishAndVerify(output)

    fun finishAndVerifySpooledFile(): File = receiver.finishAndVerifySpooledFile()

    fun close() = receiver.close()

    fun feedbackFor(
        sourceBlockNumber: Int,
        additionalRepairSymbols: Int
    ): RaptorQBlockFeedback {
        require(additionalRepairSymbols >= 0)
        val decoded = receiver.isBlockComplete(sourceBlockNumber)
        require(decoded || additionalRepairSymbols > 0) {
            "An incomplete block must request at least one repair symbol"
        }
        return RaptorQBlockFeedback(
            sourceBlockNumber = sourceBlockNumber,
            blockDecoded = decoded,
            additionalRepairSymbols = if (decoded) 0 else additionalRepairSymbols,
            uniqueSymbolsReceived = receiver.uniqueSymbolsReceived(sourceBlockNumber)
        )
    }
}
