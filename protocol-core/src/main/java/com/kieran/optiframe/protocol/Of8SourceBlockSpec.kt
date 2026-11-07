package com.kieran.optiframe.protocol

/** Small file-backed description of an OF8 block; contains no symbol payloads. */
data class Of8SourceBlockSpec(
    val blockNumber: Int,
    val k: Int,
    val originalByteCount: Int,
    val fileOffset: Long
) {
    init {
        require(blockNumber in 0..0xFFFF)
        require(k in 1..ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK)
        require(originalByteCount in 1..ProtocolConstants.OF8_SOURCE_BLOCK_MAX_BYTES)
        require(fileOffset >= 0)
        require(k == (originalByteCount + ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES - 1) /
            ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES)
    }

    companion object {
        fun fromFileSize(fileSize: Long): List<Of8SourceBlockSpec> {
            require(fileSize >= 0)
            if (fileSize == 0L) return emptyList()
            val blocks = mutableListOf<Of8SourceBlockSpec>()
            var offset = 0L
            while (offset < fileSize) {
                val count = minOf(fileSize - offset, ProtocolConstants.OF8_SOURCE_BLOCK_MAX_BYTES.toLong()).toInt()
                val k = (count + ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES - 1) /
                    ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES
                blocks += Of8SourceBlockSpec(blocks.size, k, count, offset)
                offset += count
            }
            require(blocks.size <= 0x10000) { "File exceeds the uint16 source block number range" }
            return blocks
        }
    }
}
