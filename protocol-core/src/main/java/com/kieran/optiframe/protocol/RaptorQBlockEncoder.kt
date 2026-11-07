package com.kieran.optiframe.protocol

/**
 * Backend contract for one independent RaptorQ source block.
 *
 * Implementations must return the systematic source symbol for ESI values
 * below [sourceSymbolCount] and generate repair symbols for later ESIs.
 */
interface RaptorQBlockEncoder : AutoCloseable {
    val sourceSymbolCount: Int

    fun encodeSymbol(esi: Long): RaptorQSymbol

    override fun close() = Unit
}
