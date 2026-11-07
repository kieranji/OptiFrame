package com.kieran.optiframe.protocol

/** Creates one decoder instance for each independent RaptorQ source block. */
fun interface RaptorQBlockDecoderFactory {
    fun create(sourceBlockNumber: Int, sourceSymbolCount: Int): RaptorQBlockDecoder
}
