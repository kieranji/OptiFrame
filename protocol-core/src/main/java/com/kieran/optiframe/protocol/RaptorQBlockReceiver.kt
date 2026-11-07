package com.kieran.optiframe.protocol

/** Result of offering one received symbol to a source-block decoder. */
data class RaptorQBlockReceiveResult(
    val disposition: Disposition,
    val decodedBlock: RaptorQDecodedSourceBlock? = null
) {
    enum class Disposition {
        ACCEPTED,
        DUPLICATE,
        DECODER_REJECTED,
        BLOCK_COMPLETE,
        ALREADY_COMPLETE
    }
}

/** Validates block identity, deduplicates ESIs, and exposes completed blocks. */
class RaptorQBlockReceiver(
    val sourceBlockNumber: Int,
    private val decoder: RaptorQBlockDecoder
) {
    private val seenEsis = HashSet<Long>()
    private var completed = false
    private var completedBlock: RaptorQDecodedSourceBlock? = null
    private var completedUniqueSymbols = 0
    private var decodedDataReleased = false

    init {
        require(sourceBlockNumber in 0..0xFFFF) {
            "sourceBlockNumber must fit uint16"
        }
        require(decoder.sourceSymbolCount in 1..ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK) {
            "sourceSymbolCount must be in 1.." +
                ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK
        }
    }

    val isComplete: Boolean
        get() = completed

    val decodedSourceBlock: RaptorQDecodedSourceBlock?
        get() = completedBlock

    val uniqueSymbolsReceived: Int
        get() = if (completed) completedUniqueSymbols else seenEsis.size

    fun accept(item: RaptorQTransmissionItem): RaptorQBlockReceiveResult {
        require(item.sourceBlockNumber == sourceBlockNumber) {
            "Received source block ${item.sourceBlockNumber}; expected $sourceBlockNumber"
        }

        if (completed) {
            if (!decodedDataReleased && seenEsis.add(item.esi)) {
                completedUniqueSymbols = seenEsis.size
            }
            return RaptorQBlockReceiveResult(
                disposition = RaptorQBlockReceiveResult.Disposition.ALREADY_COMPLETE,
                decodedBlock = completedBlock
            )
        }

        val expectedKind = if (item.esi < decoder.sourceSymbolCount) {
            RaptorQSymbol.Kind.SOURCE
        } else {
            RaptorQSymbol.Kind.REPAIR
        }
        require(item.kind == expectedKind) {
            "ESI ${item.esi} must be classified as $expectedKind"
        }

        if (item.esi in seenEsis) {
            return RaptorQBlockReceiveResult(
                disposition = RaptorQBlockReceiveResult.Disposition.DUPLICATE
            )
        }

        if (!decoder.addSymbol(item.symbol)) {
            return RaptorQBlockReceiveResult(
                disposition = RaptorQBlockReceiveResult.Disposition.DECODER_REJECTED
            )
        }
        seenEsis.add(item.esi)

        val recovered = decoder.decodeSourceSymbols()
            ?: return RaptorQBlockReceiveResult(
                disposition = RaptorQBlockReceiveResult.Disposition.ACCEPTED
            )

        require(recovered.size == decoder.sourceSymbolCount) {
            "Decoder returned ${recovered.size} source symbols; expected ${decoder.sourceSymbolCount}"
        }

        val symbols = recovered.mapIndexed { index, bytes ->
            Of8SourceSymbol.takeOwnership(esi = index, bytes = bytes)
        }
        val block = RaptorQDecodedSourceBlock(
            sourceBlockNumber = sourceBlockNumber,
            sourceSymbols = symbols
        )
        completedBlock = block
        completedUniqueSymbols = seenEsis.size
        completed = true
        decoder.close()

        return RaptorQBlockReceiveResult(
            disposition = RaptorQBlockReceiveResult.Disposition.BLOCK_COMPLETE,
            decodedBlock = block
        )
    }

    /** Drops recovered symbol buffers after the caller has consumed them. */
    fun releaseDecodedData() {
        check(completed) { "Cannot release a block before it is decoded" }
        completedBlock = null
        seenEsis.clear()
        decodedDataReleased = true
    }
}
