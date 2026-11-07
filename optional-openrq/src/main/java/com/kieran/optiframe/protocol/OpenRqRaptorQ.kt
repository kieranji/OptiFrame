package com.kieran.optiframe.protocol

/** Convenience constructors for the OpenRQ-backed OF8 adapters. */
object OpenRqRaptorQ {
    val decoderFactory: RaptorQBlockDecoderFactory =
        RaptorQBlockDecoderFactory { _, sourceSymbolCount ->
            OpenRqBlockDecoder(sourceSymbolCount)
        }

    fun encoder(sourceBlock: Of8SourceBlock): RaptorQBlockEncoder =
        OpenRqBlockEncoder(sourceBlock)

    fun senderSession(
        fileName: String,
        fileBytes: ByteArray,
        initialRepairSymbolsPerBlock: Int,
        createdAtUnixMillis: Long? = null,
        mimeType: String? = null
    ): RaptorQFileSenderSession = RaptorQFileSenderSession.fromBytes(
        fileName = fileName,
        fileBytes = fileBytes,
        initialRepairSymbolsPerBlock = initialRepairSymbolsPerBlock,
        encoderFactory = ::encoder,
        createdAtUnixMillis = createdAtUnixMillis,
        mimeType = mimeType
    )

    fun receiverSession(manifest: OptiFrameManifest): RaptorQFileReceiverSession =
        RaptorQFileReceiverSession(manifest, decoderFactory)

    /** OpenRQ-backed M3 sender: file symbols -> P1 frames -> SAFE30 presentations. */
    fun m3SenderSession(
        fileName: String,
        fileBytes: ByteArray,
        initialRepairSymbolsPerBlock: Int,
        sessionId: Long,
        firstFrameSequence: Long,
        positions: List<P1SlotPosition> = P1SlotPosition.canonicalP1(),
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
    ): P1M3SenderSession = P1M3SenderSession.fromBytes(
        fileName = fileName,
        fileBytes = fileBytes,
        initialRepairSymbolsPerBlock = initialRepairSymbolsPerBlock,
        sessionId = sessionId,
        firstFrameSequence = firstFrameSequence,
        positions = positions,
        encoderFactory = ::encoder,
        flagsForItem = flagsForItem,
        headerFactory = headerFactory,
        createdAtUnixMillis = createdAtUnixMillis,
        mimeType = mimeType,
        advanceAfterRepairQueue = advanceAfterRepairQueue,
        initialRepairFraction = initialRepairFraction,
        fast60 = fast60
    )

    fun m3SenderSessionFromFile(
        fileName: String,
        file: java.io.File,
        initialRepairSymbolsPerBlock: Int,
        sessionId: Long,
        firstFrameSequence: Long,
        flagsForItem: (RaptorQTransmissionItem) -> Int,
        headerFactory: (Int, Long, RaptorQSymbol.Kind) -> OptiFrameGlobalHeader,
        mimeType: String? = null,
        advanceAfterRepairQueue: Boolean = false,
        initialRepairFraction: Double? = null,
        fast60: Boolean = false
    ): P1M3SenderSession = P1M3SenderSession.fromFile(
        fileName = fileName,
        file = file,
        initialRepairSymbolsPerBlock = initialRepairSymbolsPerBlock,
        sessionId = sessionId,
        firstFrameSequence = firstFrameSequence,
        encoderFactory = { spec -> OpenRqBlockEncoder(spec, file) },
        flagsForItem = flagsForItem,
        headerFactory = headerFactory,
        mimeType = mimeType,
        advanceAfterRepairQueue = advanceAfterRepairQueue,
        initialRepairFraction = initialRepairFraction,
        fast60 = fast60
    )

    /** OpenRQ-backed M3 receiver for slot decoding and file recovery. */
    fun m3ReceiverSession(
        manifest: OptiFrameManifest,
        expectedSessionId: Long,
        positions: List<P1SlotPosition> = P1SlotPosition.canonicalP1(),
        spoolFile: java.io.File? = null
    ): P1M3ReceiverSession = P1M3ReceiverSession(
        manifest = manifest,
        decoderFactory = decoderFactory,
        expectedSessionId = expectedSessionId,
        positions = positions,
        spoolFile = spoolFile
    )
}
