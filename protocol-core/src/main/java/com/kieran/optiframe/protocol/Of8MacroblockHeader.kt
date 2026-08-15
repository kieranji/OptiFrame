package com.kieran.optiframe.protocol

data class Of8MacroblockHeader(
    val esi: Long,
    val slotIndex: Int,
    val flags: Int,
    val frameSeqLow: Int,
    val symbolLength: Int = ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES,
    val reserved: Int = 0
) {
    init {
        require(esi in 0..0xFFFF_FFFFL) {
            "ESI must fit uint32: $esi"
        }

        require(slotIndex in 0..0xFF) {
            "slotIndex must fit uint8: $slotIndex"
        }

        require(flags in 0..0xFF) {
            "flags must fit uint8: $flags"
        }

        require(frameSeqLow in 0..0xFFFF) {
            "frameSeqLow must fit uint16: $frameSeqLow"
        }

        require(symbolLength in 0..0xFFFF) {
            "symbolLength must fit uint16: $symbolLength"
        }

        require(reserved in 0..0xFFFF) {
            "reserved must fit uint16: $reserved"
        }
    }
}