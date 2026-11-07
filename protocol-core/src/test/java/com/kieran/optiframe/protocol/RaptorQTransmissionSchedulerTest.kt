package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RaptorQTransmissionSchedulerTest {
    @Test
    fun fixedRepairBudgetAdvancesBlocksWithoutFeedback() {
        val sourceBlocks = Of8FileSymbolizer.split(ByteArray(5 * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES))
        var encoderReleased = false
        val scheduler = RaptorQFileTransmissionScheduler.fromBlocks(
            blocks = sourceBlocks,
            initialRepairSymbolsPerBlock = 0,
            encoderFactory = { block ->
                object : RaptorQBlockEncoder {
                    override val sourceSymbolCount = block.k

                    override fun encodeSymbol(esi: Long): RaptorQSymbol {
                        require(esi <= RaptorQSymbol.MAX_ESI)
                        val isSource = esi < sourceSymbolCount
                        val payload = if (isSource) {
                            block.symbols[esi.toInt()].bytes
                        } else {
                            ByteArray(ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) { esi.toByte() }
                        }
                        return RaptorQSymbol(
                            esi = esi,
                            bytes = payload,
                            kind = if (isSource) RaptorQSymbol.Kind.SOURCE else RaptorQSymbol.Kind.REPAIR
                        )
                    }

                    override fun close() {
                        encoderReleased = true
                    }
                }
            },
            advanceAfterRepairQueue = true,
            initialRepairFraction = 0.20
        )

        val sent = generateSequence { scheduler.next() }.toList()

        assertEquals(6, sent.size)
        assertEquals(5, sent.count { it.symbol.kind == RaptorQSymbol.Kind.SOURCE })
        assertEquals(1, sent.count { it.symbol.kind == RaptorQSymbol.Kind.REPAIR })
        assertEquals(1, scheduler.completedBlockCount)
        assertTrue(scheduler.isComplete)
        assertTrue(encoderReleased)
    }
}

