package com.kieran.optiframe.protocol

import java.io.OutputStream
import java.security.MessageDigest

/** Receives RaptorQ source blocks, reconstructs the file, and verifies SHA-256. */
class RaptorQFileReceiver(
    private val manifest: OptiFrameManifest,
    decoderFactory: RaptorQBlockDecoderFactory
) {
    private val blockReceivers: List<RaptorQBlockReceiver>
    private val originalByteCounts: IntArray
    private var verifiedFile: ByteArray? = null
    private var verifiedToStream = false

    init {
        require(manifest.sourceSymbolBytes == ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) {
            "Manifest source symbol size does not match OF8"
        }
        require(manifest.fileSize <= Int.MAX_VALUE.toLong()) {
            "In-memory file reconstruction is limited to ${Int.MAX_VALUE} bytes"
        }

        val blockKValues = manifest.sourceBlockKValues
        originalByteCounts = originalByteCounts(
            fileSize = manifest.fileSize,
            sourceBlockKValues = blockKValues,
            sourceSymbolBytes = manifest.sourceSymbolBytes
        )
        blockReceivers = blockKValues.mapIndexed { blockNumber, k ->
            val decoder = decoderFactory.create(blockNumber, k)
            require(decoder.sourceSymbolCount == k) {
                "Decoder for source block $blockNumber reports " +
                    "K=${decoder.sourceSymbolCount}; manifest requires K=$k"
            }
            RaptorQBlockReceiver(
                sourceBlockNumber = blockNumber,
                decoder = decoder
            )
        }
    }

    val isComplete: Boolean
        get() = blockReceivers.all { it.isComplete }

    val progress: RaptorQFileReceiveProgress
        get() = RaptorQFileReceiveProgress(
            completedBlocks = blockReceivers.count { it.isComplete },
            receivedSymbolsPerBlock = blockReceivers
                .map { it.uniqueSymbolsReceived }
                .toIntArray(),
            totalBlocks = blockReceivers.size,
            isComplete = isComplete
        )

    fun isBlockComplete(sourceBlockNumber: Int): Boolean {
        require(sourceBlockNumber in blockReceivers.indices)
        return blockReceivers[sourceBlockNumber].isComplete
    }

    fun uniqueSymbolsReceived(sourceBlockNumber: Int): Int {
        require(sourceBlockNumber in blockReceivers.indices)
        return blockReceivers[sourceBlockNumber].uniqueSymbolsReceived
    }

    fun sourceSymbolCount(sourceBlockNumber: Int): Int {
        require(sourceBlockNumber in manifest.sourceBlockKValues.indices)
        return manifest.sourceBlockKValues[sourceBlockNumber]
    }

    fun accept(item: RaptorQTransmissionItem): RaptorQBlockReceiveResult {
        require(item.sourceBlockNumber in blockReceivers.indices) {
            "Source block ${item.sourceBlockNumber} is outside the manifest"
        }
        return blockReceivers[item.sourceBlockNumber].accept(item)
    }

    /** Returns the verified file bytes; no data is returned before all checks pass. */
    fun finishAndVerify(): ByteArray {
        verifiedFile?.let { return it }
        check(!verifiedToStream) { "Verified file was already written to an output stream" }
        check(isComplete) { "Not all source blocks have been decoded" }

        val fileBytes = ByteArray(manifest.fileSize.toInt())
        var fileOffset = 0
        blockReceivers.forEachIndexed { blockNumber, receiver ->
            val decoded = checkNotNull(receiver.decodedSourceBlock)
            require(decoded.k == manifest.sourceBlockKValues[blockNumber]) {
                "Decoded K for source block $blockNumber does not match the manifest"
            }
            var blockBytesRemaining = originalByteCounts[blockNumber]
            for (symbol in decoded.sourceSymbols) {
                if (blockBytesRemaining == 0) break
                val bytesToCopy = minOf(symbol.bytes.size, blockBytesRemaining)
                symbol.bytes.copyInto(
                    destination = fileBytes,
                    destinationOffset = fileOffset,
                    startIndex = 0,
                    endIndex = bytesToCopy
                )
                fileOffset += bytesToCopy
                blockBytesRemaining -= bytesToCopy
            }
            require(blockBytesRemaining == 0) {
                "Decoded source symbols do not cover source block $blockNumber"
            }
            receiver.releaseDecodedData()
        }
        check(fileOffset == fileBytes.size) { "Decoded source blocks do not cover the declared file size" }
        val actualSha256 = Sha256.digest(fileBytes)
        check(actualSha256.contentEquals(manifest.fileSha256)) {
            "Reconstructed file SHA-256 does not match the manifest"
        }

        verifiedFile = fileBytes
        return fileBytes
    }

    /** Reconstructs and verifies into a stream without allocating a file-sized byte array. */
    fun finishAndVerify(output: OutputStream) {
        check(verifiedFile == null && !verifiedToStream) { "File has already been verified" }
        check(isComplete) { "Not all source blocks have been decoded" }
        val digest = MessageDigest.getInstance("SHA-256")
        var fileBytesWritten = 0L
        blockReceivers.forEachIndexed { blockNumber, receiver ->
            val decoded = checkNotNull(receiver.decodedSourceBlock)
            require(decoded.k == manifest.sourceBlockKValues[blockNumber]) {
                "Decoded K for source block $blockNumber does not match the manifest"
            }
            var blockBytesRemaining = originalByteCounts[blockNumber]
            for (symbol in decoded.sourceSymbols) {
                if (blockBytesRemaining == 0) break
                val bytesToWrite = minOf(symbol.bytes.size, blockBytesRemaining)
                output.write(symbol.bytes, 0, bytesToWrite)
                digest.update(symbol.bytes, 0, bytesToWrite)
                fileBytesWritten += bytesToWrite
                blockBytesRemaining -= bytesToWrite
            }
            require(blockBytesRemaining == 0) {
                "Decoded source symbols do not cover source block $blockNumber"
            }
            receiver.releaseDecodedData()
        }
        check(fileBytesWritten == manifest.fileSize) {
            "Decoded source blocks wrote $fileBytesWritten bytes; manifest declares ${manifest.fileSize}"
        }
        check(digest.digest().contentEquals(manifest.fileSha256)) {
            "Reconstructed file SHA-256 does not match the manifest"
        }
        verifiedToStream = true
    }

    private fun originalByteCounts(
        fileSize: Long,
        sourceBlockKValues: IntArray,
        sourceSymbolBytes: Int
    ): IntArray {
        if (fileSize == 0L) {
            require(sourceBlockKValues.isEmpty()) {
                "Empty file must not contain source blocks"
            }
            return IntArray(0)
        }

        require(sourceBlockKValues.isNotEmpty()) {
            "Non-empty file must contain source blocks"
        }

        var remaining = fileSize
        val counts = IntArray(sourceBlockKValues.size)
        for (index in sourceBlockKValues.indices) {
            val capacity = sourceBlockKValues[index].toLong() * sourceSymbolBytes
            val count = minOf(remaining, capacity)
            require(count > 0L) {
                "Manifest contains a source block with no original file bytes"
            }
            counts[index] = count.toInt()
            remaining -= count
        }
        require(remaining == 0L) {
            "Manifest source symbols do not cover the declared file size"
        }
        return counts
    }
}
