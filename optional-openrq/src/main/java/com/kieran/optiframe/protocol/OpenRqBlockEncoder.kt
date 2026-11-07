package com.kieran.optiframe.protocol

import net.fec.openrq.ArrayDataEncoder
import net.fec.openrq.OpenRQ
import net.fec.openrq.encoder.SourceBlockEncoder
import net.fec.openrq.parameters.FECParameters
import java.io.RandomAccessFile

/** RFC 6330 adapter backed by OpenRQ for one padded OF8 source block. */
class OpenRqBlockEncoder private constructor(
    override val sourceSymbolCount: Int,
    sourceBlock: Of8SourceBlock?,
    sourceDataProvider: (() -> ByteArray)?
) : RaptorQBlockEncoder {
    private var sourceBlock: Of8SourceBlock? = sourceBlock
    private var openRqEncoder: ArrayDataEncoder? = null
    private var blockEncoder: SourceBlockEncoder? = null
    private var sourceDataProvider: (() -> ByteArray)? = sourceDataProvider

    constructor(sourceBlock: Of8SourceBlock) : this(sourceBlock.k, sourceBlock, null)

    init {
        require(sourceSymbolCount in 1..ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK)
    }

    constructor(spec: Of8SourceBlockSpec, file: java.io.File) : this(
        spec.k,
        null,
        {
            val bytes = ByteArray(spec.k * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES)
            RandomAccessFile(file, "r").use { input ->
                input.seek(spec.fileOffset)
                var offset = 0
                while (offset < spec.originalByteCount) {
                    val count = input.read(bytes, offset, spec.originalByteCount - offset)
                    check(count > 0) { "Source file changed while transfer was being prepared" }
                    offset += count
                }
            }
            bytes
        }
    )

    private fun encoder(): SourceBlockEncoder {
        blockEncoder?.let { return it }
        val sourceData = sourceDataProvider?.invoke() ?: ByteArray(sourceSymbolCount * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES).also { target ->
            val block = checkNotNull(sourceBlock) { "RaptorQ block encoder has been released" }
            block.symbols.forEachIndexed { index, symbol ->
                val bytes = symbol.bytes
                require(bytes.size == ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES)
                bytes.copyInto(target, index * ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES)
            }
        }

        val parameters = FECParameters.newParameters(
            sourceData.size.toLong(),
            ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES,
            1
        )
        val encoder = OpenRQ.newEncoder(sourceData, parameters)
        require(encoder.numberOfSourceBlocks() == 1)
        val sourceBlockEncoder = encoder.sourceBlock(0)
        require(sourceBlockEncoder.numberOfSourceSymbols() == sourceSymbolCount) {
            "OpenRQ partitioned OF8 block into an unexpected number of source symbols"
        }
        openRqEncoder = encoder
        blockEncoder = sourceBlockEncoder
        return sourceBlockEncoder
    }

    override fun encodeSymbol(esi: Long): RaptorQSymbol {
        require(esi in 0..RaptorQSymbol.MAX_ESI)
        val packet = encoder().encodingPacket(esi.toInt())
        require(packet.numberOfSymbols() == 1)
        val buffer = packet.symbols().duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        require(bytes.size == ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES)

        return RaptorQSymbol(
            esi = esi,
            bytes = bytes,
            kind = if (esi < sourceSymbolCount) {
                RaptorQSymbol.Kind.SOURCE
            } else {
                RaptorQSymbol.Kind.REPAIR
            }
        )
    }

    override fun close() {
        blockEncoder = null
        openRqEncoder = null
        sourceBlock = null
        sourceDataProvider = null
    }
}






