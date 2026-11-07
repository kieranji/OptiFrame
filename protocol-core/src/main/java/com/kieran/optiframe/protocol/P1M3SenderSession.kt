package com.kieran.optiframe.protocol

import java.io.File

/** High-level P1 sender: file symbols -> optical frames -> SAFE30 or FAST60 presentations. */
class P1M3SenderSession private constructor(
    val sessionId: Long,
    private val fileSender: RaptorQFileSenderSession,
    private val presentationScheduler: P1FramePresentationScheduler,
    val fast60: Boolean,
    private var paused: Boolean = false,
    private var cancelled: Boolean = false,
    private var receiverComplete: Boolean = false
) {
    val manifest: OptiFrameManifest
        get() = fileSender.manifest

    val progress: RaptorQFileSendProgress
        get() = synchronized(this) { fileSender.progress }

    val isPaused: Boolean
        get() = synchronized(this) { paused }

    val isStopped: Boolean
        get() = synchronized(this) { cancelled || receiverComplete }

    val isCancelled: Boolean
        get() = synchronized(this) { cancelled }

    val receiverReportedComplete: Boolean
        get() = synchronized(this) { receiverComplete }

    /** Most recent display frame, including while the sender waits for feedback. */
    val lastPresentation: P1Safe30Presentation?
        get() = synchronized(this) { presentationScheduler.lastPresentation }

    fun nextVsyncPresentation(): P1Safe30Presentation? = synchronized(this) {
        if (paused || cancelled || receiverComplete) null else presentationScheduler.nextPresentation()
    }

    fun applyFeedback(feedback: RaptorQBlockFeedback) = synchronized(this) {
        fileSender.applyFeedback(feedback)
    }

    /** Validates the session and applies M4 BLE feedback or flow-control commands. */
    fun applyControlMessage(message: P1ControlMessage) = synchronized(this) {
        require(message.sessionId == sessionId) { "BLE control message belongs to another session" }
        when (message) {
            is P1ControlMessage.BlockDecoded,
            is P1ControlMessage.RepairRequested -> {
                check(!cancelled && !receiverComplete) { "Transfer has already stopped" }
                fileSender.applyFeedback(checkNotNull(P1ControlMessageCodec.toFeedback(message)))
            }
            is P1ControlMessage.Command -> when (message.action) {
                P1ControlMessage.Action.PAUSE -> if (!cancelled && !receiverComplete) paused = true
                P1ControlMessage.Action.RESUME -> if (!cancelled && !receiverComplete) paused = false
                P1ControlMessage.Action.CANCEL -> cancelled = true
                P1ControlMessage.Action.TRANSFER_COMPLETE -> receiverComplete = true
            }
        }
    }

    companion object {
        fun fromFile(
            fileName: String,
            file: File,
            initialRepairSymbolsPerBlock: Int,
            sessionId: Long,
            firstFrameSequence: Long,
            positions: List<P1SlotPosition> = P1SlotPosition.canonicalP1(),
            encoderFactory: (Of8SourceBlockSpec) -> RaptorQBlockEncoder,
            flagsForItem: (RaptorQTransmissionItem) -> Int,
            headerFactory: (Int, Long, RaptorQSymbol.Kind) -> OptiFrameGlobalHeader,
            createdAtUnixMillis: Long? = null,
            mimeType: String? = null,
            advanceAfterRepairQueue: Boolean = false,
            initialRepairFraction: Double? = null,
            fast60: Boolean = false
        ): P1M3SenderSession {
            val sender = RaptorQFileSenderSession.fromFile(
                fileName, file, initialRepairSymbolsPerBlock, encoderFactory,
                createdAtUnixMillis, mimeType, advanceAfterRepairQueue, initialRepairFraction
            )
            val frameSender = P1RaptorQFrameSender(sender, sessionId, positions, flagsForItem, headerFactory)
            return P1M3SenderSession(
                sessionId, sender,
                if (fast60) P1Fast60FrameScheduler(frameSender, firstFrameSequence)
                else P1Safe30FrameScheduler(frameSender, firstFrameSequence),
                fast60
            )
        }

        fun fromBytes(
            fileName: String,
            fileBytes: ByteArray,
            initialRepairSymbolsPerBlock: Int,
            sessionId: Long,
            firstFrameSequence: Long,
            positions: List<P1SlotPosition> = P1SlotPosition.canonicalP1(),
            encoderFactory: (Of8SourceBlock) -> RaptorQBlockEncoder,
            flagsForItem: (RaptorQTransmissionItem) -> Int,
            headerFactory: (
                sourceBlockNumber: Int,
                frameSequence: Long,
                symbolKind: RaptorQSymbol.Kind
            ) -> OptiFrameGlobalHeader,
            createdAtUnixMillis: Long? = null,
            mimeType: String? = null,
            advanceAfterRepairQueue: Boolean = false,
            initialRepairFraction: Double? = null,
            fast60: Boolean = false
        ): P1M3SenderSession {
            val sender = RaptorQFileSenderSession.fromBytes(
                fileName = fileName,
                fileBytes = fileBytes,
                initialRepairSymbolsPerBlock = initialRepairSymbolsPerBlock,
                encoderFactory = encoderFactory,
                createdAtUnixMillis = createdAtUnixMillis,
                mimeType = mimeType,
                advanceAfterRepairQueue = advanceAfterRepairQueue,
                initialRepairFraction = initialRepairFraction
            )
            val frameSender = P1RaptorQFrameSender(
                senderSession = sender,
                sessionId = sessionId,
                positions = positions,
                flagsForItem = flagsForItem,
                headerFactory = headerFactory
            )
            return P1M3SenderSession(
                sessionId = sessionId,
                fileSender = sender,
                presentationScheduler = if (fast60) {
                    P1Fast60FrameScheduler(frameSender, firstFrameSequence)
                } else {
                    P1Safe30FrameScheduler(frameSender, firstFrameSequence)
                },
                fast60 = fast60
            )
        }
    }
}
