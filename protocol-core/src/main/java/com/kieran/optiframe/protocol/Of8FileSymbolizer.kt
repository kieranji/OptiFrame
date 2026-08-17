package com.kieran.optiframe.protocol

object Of8FileSymbolizer {

    private const val SYMBOL_BYTES =
        ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES

    private const val MAX_SYMBOLS_PER_BLOCK =
        ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK

    private const val MAX_BLOCK_BYTES =
        ProtocolConstants.OF8_SOURCE_BLOCK_MAX_BYTES

    fun split(
        fileBytes: ByteArray
    ): List<Of8SourceBlock> {

        /*
         * Empty file:
         *
         * no source symbols and no source blocks.
         *
         * The file length remains 0 and reconstruction therefore
         * produces an empty ByteArray.
         *
         * The Draft 0.1 manual requires a 0 B file round-trip but
         * does not explicitly define a wire-level empty RaptorQ
         * block, so we do not invent one here.
         */
        if (fileBytes.isEmpty()) {
            return emptyList()
        }

        val blocks =
            mutableListOf<Of8SourceBlock>()

        var fileOffset = 0
        var blockNumber = 0

        while (fileOffset < fileBytes.size) {

            val remainingFileBytes =
                fileBytes.size - fileOffset

            val blockByteCount =
                minOf(
                    remainingFileBytes,
                    MAX_BLOCK_BYTES
                )

            val blockEnd =
                fileOffset + blockByteCount

            val k =
                (
                        blockByteCount +
                                SYMBOL_BYTES -
                                1
                        ) / SYMBOL_BYTES

            check(
                k in 1..MAX_SYMBOLS_PER_BLOCK
            )

            val symbols =
                ArrayList<Of8SourceSymbol>(k)

            for (esi in 0 until k) {

                val symbol =
                    ByteArray(SYMBOL_BYTES)

                val sourceStart =
                    fileOffset +
                            esi * SYMBOL_BYTES

                val sourceEnd =
                    minOf(
                        sourceStart + SYMBOL_BYTES,
                        blockEnd
                    )

                val actualLength =
                    sourceEnd - sourceStart

                if (actualLength > 0) {

                    fileBytes.copyInto(
                        destination = symbol,
                        destinationOffset = 0,
                        startIndex = sourceStart,
                        endIndex = sourceEnd
                    )
                }

                /*
                 * Remaining bytes automatically stay 0x00.
                 */
                symbols +=
                    Of8SourceSymbol(
                        esi = esi,
                        bytes = symbol
                    )
            }

            blocks +=
                Of8SourceBlock(
                    blockNumber = blockNumber,
                    originalByteCount = blockByteCount,
                    symbols = symbols
                )

            fileOffset =
                blockEnd

            blockNumber++
        }

        return blocks
    }

    fun reconstruct(
        blocks: List<Of8SourceBlock>,
        originalFileSize: Int
    ): ByteArray {

        require(originalFileSize >= 0) {
            "originalFileSize must be non-negative"
        }

        if (originalFileSize == 0) {

            require(blocks.isEmpty()) {
                "0-byte file must not contain source blocks"
            }

            return ByteArray(0)
        }

        require(blocks.isNotEmpty()) {
            "Non-empty file requires source blocks"
        }

        /*
         * Require sequential block numbering.
         */
        blocks.forEachIndexed { index, block ->

            require(
                block.blockNumber == index
            ) {
                "Expected source block $index, " +
                        "got ${block.blockNumber}"
            }
        }

        val paddedSize =
            blocks.sumOf { block ->
                block.symbols.size *
                        SYMBOL_BYTES
            }

        val padded =
            ByteArray(paddedSize)

        var destinationOffset = 0

        for (block in blocks) {

            for (symbol in block.symbols) {

                symbol.bytes.copyInto(
                    destination = padded,
                    destinationOffset =
                        destinationOffset
                )

                destinationOffset +=
                    SYMBOL_BYTES
            }
        }

        require(
            originalFileSize <= padded.size
        ) {
            "Original file size exceeds reconstructed data"
        }

        /*
         * Remove final zero padding according to
         * manifest/original file length.
         */
        return padded.copyOf(
            originalFileSize
        )
    }
}