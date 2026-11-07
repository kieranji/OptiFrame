package com.kieran.optiframe.protocol

/** One packed optical frame and the transport symbols placed into it. */
data class P1PackedRaptorQFrame(
    val frame: P1LogicalOpticalFrame,
    val transmissionItems: List<RaptorQTransmissionItem>
)

/** Successfully recovered transport symbols plus independently failed slots. */
data class P1UnpackedRaptorQFrame(
    val sourceBlockNumber: Int,
    val frameSequence: Long,
    val transmissionItems: List<RaptorQTransmissionItem>,
    val slotFailures: List<P1SlotDecodeFailure>
) {
    init {
        require(sourceBlockNumber in 0..0xFFFF)
        require(frameSequence in 0..0xFFFF_FFFFL)
        require(transmissionItems.all { it.sourceBlockNumber == sourceBlockNumber }) {
            "An optical P1 frame must not mix source blocks"
        }
    }
}

/** Bridges RaptorQ symbols to and from explicitly positioned P1 OF8 slots. */
object P1RaptorQFrameCodec {
    fun pack(
        globalHeader: OptiFrameGlobalHeader,
        items: List<RaptorQTransmissionItem>,
        positions: List<P1SlotPosition>,
        flagsForItem: (RaptorQTransmissionItem) -> Int
    ): P1PackedRaptorQFrame {
        require(globalHeader.profileId == ProtocolConstants.PROFILE_P1_ID)
        require(items.isNotEmpty()) { "A data frame must contain at least one symbol" }
        require(items.size <= ProtocolConstants.P1_MACROBLOCK_COUNT)
        require(positions.size == items.size) {
            "Expected one explicit P1 slot position per transmission item"
        }
        P1DataRegionExtractor.validatePositions(positions)

        val blockNumber = items.first().sourceBlockNumber
        require(items.all { it.sourceBlockNumber == blockNumber }) {
            "One optical data frame cannot mix source blocks"
        }
        require(globalHeader.sourceBlockNumber == blockNumber) {
            "Global header source block does not match its transmission items"
        }
        val symbolKind = items.first().symbol.kind
        require(items.all { it.symbol.kind == symbolKind }) {
            "One P1 frame cannot mix source and repair symbols"
        }
        val expectedFrameType = when (symbolKind) {
            RaptorQSymbol.Kind.SOURCE -> ProtocolConstants.FRAME_TYPE_DATA
            RaptorQSymbol.Kind.REPAIR -> ProtocolConstants.FRAME_TYPE_REPAIR
        }
        require(globalHeader.frameType == expectedFrameType) {
            "Frame type ${globalHeader.frameType} does not match $symbolKind symbols"
        }
        require(items.map { it.esi }.toSet().size == items.size) {
            "One P1 frame cannot contain the same ESI more than once"
        }

        val lowFrameSequence = (globalHeader.frameSequence and 0xFFFFL).toInt()
        val slots = items.zip(positions).map { (item, position) ->
            require(item.esi in 0..0xFFFF_FFFFL) {
                "OF8 macroblock ESI must fit uint32"
            }
            val macroblock = Of8Macroblock(
                header = Of8MacroblockHeader(
                    esi = item.esi,
                    slotIndex = position.slotIndex,
                    flags = flagsForItem(item),
                    frameSeqLow = lowFrameSequence
                ),
                payload = item.symbol.bytes
            )
            P1LogicalFrameSlot.fromMacroblock(
                globalHeader = globalHeader,
                macroblock = macroblock,
                macroblockColumn = position.macroblockColumn,
                macroblockRow = position.macroblockRow
            )
        }

        return P1PackedRaptorQFrame(
            frame = P1LogicalFrameBuilder.build(globalHeader, slots),
            transmissionItems = items.toList()
        )
    }

    fun unpack(
        frame: P1LogicalOpticalFrame,
        positions: List<P1SlotPosition>,
        sourceSymbolCount: Int,
        fullGridConfidence: DoubleArray? = null,
        erasureThreshold: Double = 0.0
    ): P1UnpackedRaptorQFrame {
        require(sourceSymbolCount in 1..ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK)
        val decoded = P1LogicalFrameDecoder.decode(
            frame = frame,
            positions = positions,
            fullGridConfidence = fullGridConfidence,
            erasureThreshold = erasureThreshold
        )
        val expectedKind = when (frame.header.frameType) {
            ProtocolConstants.FRAME_TYPE_DATA -> RaptorQSymbol.Kind.SOURCE
            ProtocolConstants.FRAME_TYPE_REPAIR -> RaptorQSymbol.Kind.REPAIR
            else -> throw IllegalArgumentException(
                "P1 RaptorQ data slots require DATA or REPAIR frame type"
            )
        }
        val items = decoded.slots.map { slotResult ->
            val macroblock = slotResult.macroblock
            val esi = macroblock.header.esi
            val kind = if (esi < sourceSymbolCount) {
                RaptorQSymbol.Kind.SOURCE
            } else {
                RaptorQSymbol.Kind.REPAIR
            }
            require(kind == expectedKind) {
                "Frame type ${frame.header.frameType} contains $kind ESI $esi"
            }
            RaptorQTransmissionItem(
                sourceBlockNumber = frame.header.sourceBlockNumber,
                symbol = RaptorQSymbol(
                    esi = esi,
                    bytes = macroblock.payload,
                    kind = kind
                )
            )
        }
        return P1UnpackedRaptorQFrame(
            sourceBlockNumber = frame.header.sourceBlockNumber,
            frameSequence = frame.header.frameSequence,
            transmissionItems = items,
            slotFailures = decoded.failures
        )
    }
}
