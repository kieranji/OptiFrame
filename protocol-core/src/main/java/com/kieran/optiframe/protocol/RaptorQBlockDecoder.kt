package com.kieran.optiframe.protocol

/** Backend contract for decoding one independent RaptorQ source block. */
interface RaptorQBlockDecoder : AutoCloseable {
    val sourceSymbolCount: Int

    /** Returns true when the symbol was accepted as new input. */
    fun addSymbol(symbol: RaptorQSymbol): Boolean

    /** Returns all K recovered source symbols once decoding succeeds, otherwise null. */
    fun decodeSourceSymbols(): List<ByteArray>?

    override fun close() = Unit
}
