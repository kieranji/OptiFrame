package com.kieran.optiframe.protocol

class OptiFrameManifest(
    val fileName: String,
    val fileSize: Long,
    fileSha256: ByteArray,
    val sourceSymbolBytes: Int,
    sourceBlockKValues: IntArray,
    val createdAtUnixMillis: Long? = null,
    val mimeType: String? = null
) {

    val fileSha256: ByteArray =
        fileSha256.copyOf()

    val sourceBlockKValues: IntArray =
        sourceBlockKValues.copyOf()

    init {
        require(fileSize >= 0) {
            "fileSize must be non-negative"
        }

        require(this.fileSha256.size == 32) {
            "SHA-256 digest must contain exactly 32 bytes"
        }

        require(sourceSymbolBytes > 0) {
            "sourceSymbolBytes must be positive"
        }

        this.sourceBlockKValues.forEachIndexed { index, k ->

            require(
                k in 1..
                        ProtocolConstants.MAX_SOURCE_SYMBOLS_PER_BLOCK
            ) {
                "Invalid K=$k for source block $index"
            }
        }

        require(
            createdAtUnixMillis == null ||
                    createdAtUnixMillis >= 0
        ) {
            "createdAtUnixMillis must be non-negative"
        }
    }

    val sourceBlockCount: Int
        get() = sourceBlockKValues.size

    fun fileSha256Hex(): String {
        return fileSha256.joinToString("") {
            "%02x".format(
                it.toInt() and 0xFF
            )
        }
    }
}