package com.kieran.optiframe.protocol

/** Pulls a bounded homogeneous batch from a file sender and packs one P1 frame. */
class P1RaptorQFrameSender(
    private val senderSession: RaptorQFileSenderSession,
    private val sessionId: Long,
    positions: List<P1SlotPosition>,
    private val flagsForItem: (RaptorQTransmissionItem) -> Int,
    private val headerFactory: (
        sourceBlockNumber: Int,
        frameSequence: Long,
        symbolKind: RaptorQSymbol.Kind
    ) -> OptiFrameGlobalHeader
) {
    private val slotPositions = positions.toList()
    private val pendingItems = ArrayList<RaptorQTransmissionItem>()

    init {
        require(sessionId in 0..0xFFFF_FFFFL) { "sessionId must fit uint32" }
        require(slotPositions.isNotEmpty()) { "At least one P1 slot position is required" }
        require(slotPositions.size <= ProtocolConstants.P1_MACROBLOCK_COUNT)
        P1DataRegionExtractor.validatePositions(slotPositions)
    }

    /** Returns null while waiting for feedback or after the file is complete. */
    fun nextFrame(frameSequence: Long): P1PackedRaptorQFrame? {
        require(frameSequence in 0..0xFFFF_FFFFL)
        if (pendingItems.isEmpty()) senderSession.prepareNextBlock()
        val blockNumber = pendingItems.firstOrNull()?.sourceBlockNumber
            ?: senderSession.controller.currentSourceBlockNumber
            ?: return null
        val symbolKind = pendingItems.firstOrNull()?.symbol?.kind
            ?: senderSession.controller.nextSymbolKind
            ?: return null
        val expectedFrameType = when (symbolKind) {
            RaptorQSymbol.Kind.SOURCE -> ProtocolConstants.FRAME_TYPE_DATA
            RaptorQSymbol.Kind.REPAIR -> ProtocolConstants.FRAME_TYPE_REPAIR
        }
        val header = headerFactory(blockNumber, frameSequence, symbolKind)
        require(header.profileId == ProtocolConstants.PROFILE_P1_ID)
        require(header.sessionId == sessionId) {
            "Global header session ID does not match this sender session"
        }
        require(header.sourceBlockNumber == blockNumber)
        require(header.frameSequence == frameSequence)
        require(header.frameType == expectedFrameType)

        if (pendingItems.isEmpty()) {
            while (pendingItems.size < slotPositions.size &&
                senderSession.controller.currentSourceBlockNumber == blockNumber &&
                senderSession.controller.nextSymbolKind == symbolKind
            ) {
                pendingItems += senderSession.nextTransmission() ?: break
            }
        }
        if (pendingItems.isEmpty()) return null

        val packed = P1RaptorQFrameCodec.pack(
            globalHeader = header,
            items = pendingItems.toList(),
            positions = slotPositions.take(pendingItems.size),
            flagsForItem = flagsForItem
        )
        pendingItems.clear()
        return packed
    }
}
