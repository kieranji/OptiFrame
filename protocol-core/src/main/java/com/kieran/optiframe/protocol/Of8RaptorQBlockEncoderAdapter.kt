package com.kieran.optiframe.protocol

/** Supplies systematic OF8 symbols and delegates repair generation to an RFC 6330 backend. */
class Of8RaptorQBlockEncoderAdapter(
    private val sourceBlock: Of8SourceBlock,
    private val repairSymbolProvider: RepairSymbolProvider
) : RaptorQBlockEncoder {
    override val sourceSymbolCount: Int
        get() = sourceBlock.k

    override fun encodeSymbol(esi: Long): RaptorQSymbol {
        require(esi in 0..RaptorQSymbol.MAX_ESI) {
            "ESI must fit the RFC 6330 24-bit field"
        }

        if (esi < sourceBlock.k) {
            val source = sourceBlock.symbols[esi.toInt()]
            return RaptorQSymbol(
                esi = esi,
                bytes = source.bytes,
                kind = RaptorQSymbol.Kind.SOURCE
            )
        }

        return RaptorQSymbol(
            esi = esi,
            bytes = repairSymbolProvider.encodeRepair(esi),
            kind = RaptorQSymbol.Kind.REPAIR
        )
    }

    fun interface RepairSymbolProvider {
        fun encodeRepair(esi: Long): ByteArray
    }
}
