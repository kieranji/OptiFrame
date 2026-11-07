package com.kieran.optiframe.protocol

import net.fec.openrq.ArrayDataDecoder
import net.fec.openrq.OpenRQ
import net.fec.openrq.decoder.SourceBlockDecoder
import net.fec.openrq.parameters.FECParameters

/** RFC 6330 decoder adapter backed by OpenRQ for one padded OF8 source block. */
class OpenRqBlockDecoder(override val sourceSymbolCount: Int) : RaptorQBlockDecoder {
    private var decoder: ArrayDataDecoder? = null
    private var blockDecoder: SourceBlockDecoder? = null
    init {
        require(sourceSymbolCount in 1..ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK)
    }

    private fun decoderAndBlock(): Pair<ArrayDataDecoder, SourceBlockDecoder> {
        val currentDecoder = decoder
        val currentBlockDecoder = blockDecoder
        if (currentDecoder != null && currentBlockDecoder != null) {
            return currentDecoder to currentBlockDecoder
        }
        val byteCount = sourceSymbolCount * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
        val parameters = FECParameters.newParameters(
            byteCount.toLong(),
            ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES,
            1
        )
        val createdDecoder = OpenRQ.newDecoderWithZeroOverhead(parameters)
        require(createdDecoder.numberOfSourceBlocks() == 1)
        val createdBlockDecoder = createdDecoder.sourceBlock(0)
        require(createdBlockDecoder.numberOfSourceSymbols() == sourceSymbolCount)
        decoder = createdDecoder
        blockDecoder = createdBlockDecoder
        return createdDecoder to createdBlockDecoder
    }

    override fun addSymbol(symbol: RaptorQSymbol): Boolean {
        require(symbol.esi <= RaptorQSymbol.MAX_ESI) { "ESI exceeds RFC 6330 range" }
        val expectedKind = if (symbol.esi < sourceSymbolCount) {
            RaptorQSymbol.Kind.SOURCE
        } else {
            RaptorQSymbol.Kind.REPAIR
        }
        require(symbol.kind == expectedKind) {
            "ESI ${symbol.esi} must be classified as $expectedKind"
        }
        val (decoder, blockDecoder) = decoderAndBlock()
        if (symbol.esi < sourceSymbolCount) {
            if (blockDecoder.containsSourceSymbol(symbol.esi.toInt())) return false
        } else if (blockDecoder.containsRepairSymbol(symbol.esi.toInt())) {
            return false
        }
        val parsed = decoder.parsePacket(
            0,
            symbol.esi.toInt(),
            symbol.bytes,
            true
        )
        if (!parsed.isValid) return false
        val packet = parsed.value()
        blockDecoder.putEncodingPacket(packet)
        return true
    }

    override fun decodeSourceSymbols(): List<ByteArray>? {
        val (decoder, blockDecoder) = decoderAndBlock()
        if (!blockDecoder.isSourceBlockDecoded()) return null
        val source = decoder.dataArray()
        val symbolBytes = ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
        require(source.size == sourceSymbolCount * symbolBytes)
        return List(sourceSymbolCount) { index ->
            source.copyOfRange(index * symbolBytes, (index + 1) * symbolBytes)
        }
    }

    override fun close() {
        blockDecoder = null
        decoder = null
    }
}





