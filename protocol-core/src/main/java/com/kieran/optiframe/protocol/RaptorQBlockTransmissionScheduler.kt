package com.kieran.optiframe.protocol

/**
 * Schedules systematic source symbols followed by feedback-driven repairs for
 * one source block. Instances are intended to be called from one worker.
 */
class RaptorQBlockTransmissionScheduler(
    val sourceBlockNumber: Int,
    private val encoderFactory: () -> RaptorQBlockEncoder,
    val sourceSymbolCount: Int,
    initialRepairSymbolCount: Int
) {
    private var encoder: RaptorQBlockEncoder? = null
    private var nextSourceEsi = 0L
    private var nextRepairEsi: Long = sourceSymbolCount.toLong()
    private var repairSymbolsRemaining = initialRepairSymbolCount
    private var completed = false
    private var sentSymbolCount = 0

    init {
        require(sourceBlockNumber in 0..0xFFFF) {
            "sourceBlockNumber must fit uint16"
        }
        require(sourceSymbolCount in 1..ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK) {
            "sourceSymbolCount must be in 1.." +
                ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK
        }
        require(initialRepairSymbolCount >= 0) {
            "initialRepairSymbolCount must be non-negative"
        }
    }

    constructor(
        sourceBlockNumber: Int,
        encoder: RaptorQBlockEncoder,
        initialRepairSymbolCount: Int
    ) : this(sourceBlockNumber, { encoder }, encoder.sourceSymbolCount, initialRepairSymbolCount) {
        this.encoder = encoder
    }

    val isComplete: Boolean
        get() = completed

    val nextSymbolKind: RaptorQSymbol.Kind?
        get() = when {
            completed -> null
            nextSourceEsi < sourceSymbolCount -> RaptorQSymbol.Kind.SOURCE
            repairSymbolsRemaining > 0 -> RaptorQSymbol.Kind.REPAIR
            else -> null
        }

    val symbolsSent: Int
        get() = sentSymbolCount

    /** Returns the next symbol, or null while awaiting feedback/no more symbols are queued. */
    fun next(): RaptorQTransmissionItem? {
        if (completed) return null

        val sendingSource = nextSourceEsi < sourceSymbolCount
        if (!sendingSource && repairSymbolsRemaining == 0) return null

        val esi = if (sendingSource) nextSourceEsi else nextRepairEsi
        if (!sendingSource) {
            require(esi <= RaptorQSymbol.MAX_ESI) {
                "RaptorQ ESI exceeds the RFC 6330 24-bit limit"
            }
        }

        val expectedKind = if (esi < sourceSymbolCount) {
            RaptorQSymbol.Kind.SOURCE
        } else {
            RaptorQSymbol.Kind.REPAIR
        }
        val activeEncoder = encoder ?: encoderFactory().also { created ->
            require(created.sourceSymbolCount == sourceSymbolCount) { "Encoder K does not match source block" }
            encoder = created
        }
        val symbol = activeEncoder.encodeSymbol(esi)
        require(symbol.esi == esi) {
            "RaptorQ backend returned ESI ${symbol.esi} for requested ESI $esi"
        }
        require(symbol.kind == expectedKind) {
            "RaptorQ backend returned ${symbol.kind} for ESI $esi; expected $expectedKind"
        }

        if (sendingSource) {
            nextSourceEsi++
        } else {
            nextRepairEsi++
            repairSymbolsRemaining--
        }
        sentSymbolCount = Math.addExact(sentSymbolCount, 1)

        return RaptorQTransmissionItem(
            sourceBlockNumber = sourceBlockNumber,
            symbol = symbol
        )
    }

    /** Adds repair symbols after a receiver reports that this block is incomplete. */
    fun requestMoreRepairSymbols(count: Int) {
        require(!completed) { "Completed source block cannot receive more repairs" }
        require(count > 0) { "count must be positive" }
        repairSymbolsRemaining = Math.addExact(repairSymbolsRemaining, count)
    }

    /** Marks this block decoded so the file scheduler may advance. */
    fun markComplete() {
        completed = true
        repairSymbolsRemaining = 0
        encoder?.close()
        encoder = null
    }
}
