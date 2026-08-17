package com.kieran.optiframe.protocol

object OptiFrameManifestBuilder {

    fun buildOf8(
        fileName: String,
        fileBytes: ByteArray,
        sourceBlocks: List<Of8SourceBlock>,
        createdAtUnixMillis: Long? = null,
        mimeType: String? = null
    ): OptiFrameManifest {

        validateBlocks(
            fileBytes = fileBytes,
            sourceBlocks = sourceBlocks
        )

        return OptiFrameManifest(
            fileName = fileName,
            fileSize = fileBytes.size.toLong(),
            fileSha256 =
                Sha256.digest(fileBytes),
            sourceSymbolBytes =
                ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES,
            sourceBlockKValues =
                IntArray(sourceBlocks.size) { index ->
                    sourceBlocks[index].k
                },
            createdAtUnixMillis =
                createdAtUnixMillis,
            mimeType =
                mimeType
        )
    }

    private fun validateBlocks(
        fileBytes: ByteArray,
        sourceBlocks: List<Of8SourceBlock>
    ) {

        if (fileBytes.isEmpty()) {

            require(sourceBlocks.isEmpty()) {
                "Empty file must currently use zero source blocks"
            }

            return
        }

        require(sourceBlocks.isNotEmpty()) {
            "Non-empty file must contain source blocks"
        }

        sourceBlocks.forEachIndexed { index, block ->

            require(block.blockNumber == index) {
                "Expected block number $index, " +
                        "got ${block.blockNumber}"
            }
        }

        val representedOriginalBytes =
            sourceBlocks.sumOf {
                it.originalByteCount.toLong()
            }

        require(
            representedOriginalBytes ==
                    fileBytes.size.toLong()
        ) {
            "Source blocks represent " +
                    "$representedOriginalBytes bytes, " +
                    "but file has ${fileBytes.size} bytes"
        }
    }
}