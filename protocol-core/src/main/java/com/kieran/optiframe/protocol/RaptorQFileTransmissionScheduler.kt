package com.kieran.optiframe.protocol

/**
 * Sends source blocks in order, optionally advancing after a fixed repair queue
 * when no receiver feedback channel is available.
 */
class RaptorQFileTransmissionScheduler(
    blocks: List<RaptorQBlockTransmissionScheduler>,
    private val advanceAfterRepairQueue: Boolean = false
) {
    private val blocks = blocks.toList()
    private var currentIndex = 0

    init {
        this.blocks.forEachIndexed { index, block ->
            require(block.sourceBlockNumber == index) {
                "Expected source block $index, got ${block.sourceBlockNumber}"
            }
        }
    }

    private fun advanceExhaustedRepairQueues() {
        while (advanceAfterRepairQueue && currentIndex < blocks.size &&
            blocks[currentIndex].nextSymbolKind == null
        ) {
            blocks[currentIndex].markComplete()
            currentIndex++
        }
    }

    val currentSourceBlockNumber: Int?
        get() = blocks.getOrNull(currentIndex)?.sourceBlockNumber

    val totalSourceBlockCount: Int
        get() = blocks.size

    val completedBlockCount: Int
        get() = currentIndex

    val currentBlockSymbolsSent: Int
        get() = blocks.getOrNull(currentIndex)?.symbolsSent ?: 0

    val currentBlockSourceSymbolCount: Int
        get() = blocks.getOrNull(currentIndex)?.sourceSymbolCount ?: 0

    val nextSymbolKind: RaptorQSymbol.Kind?
        get() = blocks.getOrNull(currentIndex)?.nextSymbolKind

    val completedSymbolsSent: Int
        get() = blocks.take(currentIndex).sumOf { it.symbolsSent }

    val isComplete: Boolean
        get() = currentIndex >= blocks.size

    /** Advances past exhausted fixed repair queues when the renderer requests the next frame. */
    fun prepareNextBlock() {
        advanceExhaustedRepairQueues()
    }

    /** Returns the next symbol, or null while awaiting feedback or after all queued symbols. */
    fun next(): RaptorQTransmissionItem? {
        advanceExhaustedRepairQueues()
        if (isComplete) return null
        val current = blocks[currentIndex]
        return current.next()
    }

    fun requestMoreRepairSymbols(count: Int) {
        check(!isComplete) { "All source blocks are complete" }
        blocks[currentIndex].requestMoreRepairSymbols(count)
    }

    /** Advances only after the receiver confirms that the current block decoded. */
    fun markCurrentBlockComplete() {
        check(!isComplete) { "All source blocks are complete" }
        blocks[currentIndex].markComplete()
        currentIndex++
    }

    companion object {
        /** File-backed blocks construct their encoder only when that block starts transmitting. */
        fun fromSpecs(
            specs: List<Of8SourceBlockSpec>,
            initialRepairSymbolsPerBlock: Int,
            encoderFactory: (Of8SourceBlockSpec) -> RaptorQBlockEncoder,
            advanceAfterRepairQueue: Boolean = false,
            initialRepairFraction: Double? = null
        ): RaptorQFileTransmissionScheduler {
            require(initialRepairSymbolsPerBlock >= 0)
            require(initialRepairFraction == null || initialRepairFraction in 0.0..1.0)
            val schedulers = specs.mapIndexed { index, spec ->
                require(spec.blockNumber == index)
                RaptorQBlockTransmissionScheduler(
                    sourceBlockNumber = spec.blockNumber,
                    encoderFactory = { encoderFactory(spec) },
                    sourceSymbolCount = spec.k,
                    initialRepairSymbolCount = initialRepairFraction?.let {
                        kotlin.math.ceil(spec.k * it).toInt()
                    } ?: initialRepairSymbolsPerBlock
                )
            }
            return RaptorQFileTransmissionScheduler(schedulers, advanceAfterRepairQueue)
        }

        /**
         * Builds per-block schedulers from the existing OF8 file symbolizer.
         * Repair generation remains supplied by the selected RFC 6330 backend.
         */
        fun fromFile(
            fileBytes: ByteArray,
            initialRepairSymbolsPerBlock: Int,
            repairProviderFactory: (Of8SourceBlock) ->
                Of8RaptorQBlockEncoderAdapter.RepairSymbolProvider
        ): RaptorQFileTransmissionScheduler {
            require(initialRepairSymbolsPerBlock >= 0) {
                "initialRepairSymbolsPerBlock must be non-negative"
            }

            val schedulers = Of8FileSymbolizer.split(fileBytes).map { sourceBlock ->
                val encoder = Of8RaptorQBlockEncoderAdapter(
                    sourceBlock = sourceBlock,
                    repairSymbolProvider = repairProviderFactory(sourceBlock)
                )
                RaptorQBlockTransmissionScheduler(
                    sourceBlockNumber = sourceBlock.blockNumber,
                    encoder = encoder,
                    initialRepairSymbolCount = initialRepairSymbolsPerBlock
                )
            }

            return RaptorQFileTransmissionScheduler(schedulers)
        }

        /** Builds schedulers from stable source blocks and an injected backend. */
        fun fromBlocks(
            blocks: List<Of8SourceBlock>,
            initialRepairSymbolsPerBlock: Int,
            encoderFactory: (Of8SourceBlock) -> RaptorQBlockEncoder,
            advanceAfterRepairQueue: Boolean = false,
            initialRepairFraction: Double? = null
        ): RaptorQFileTransmissionScheduler {
            require(initialRepairSymbolsPerBlock >= 0)
            require(initialRepairFraction == null || initialRepairFraction in 0.0..1.0) {
                "initialRepairFraction must be between 0 and 1"
            }
            val stableBlocks = blocks.toList()
            val schedulers = stableBlocks.mapIndexed { expectedBlock, sourceBlock ->
                require(sourceBlock.blockNumber == expectedBlock) {
                    "Expected source block $expectedBlock, got ${sourceBlock.blockNumber}"
                }
                require(sourceBlock.k > 0) { "RaptorQ source blocks must not be empty" }
                RaptorQBlockTransmissionScheduler(
                    sourceBlockNumber = sourceBlock.blockNumber,
                    encoder = encoderFactory(sourceBlock),
                    initialRepairSymbolCount = initialRepairFraction?.let { fraction ->
                        kotlin.math.ceil(sourceBlock.k * fraction).toInt()
                    } ?: initialRepairSymbolsPerBlock
                )
            }
            return RaptorQFileTransmissionScheduler(schedulers, advanceAfterRepairQueue)
        }
    }
}
