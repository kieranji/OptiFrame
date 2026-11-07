package com.kieran.optiframe.protocol

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Receives RaptorQ source blocks, reconstructs the file, and verifies SHA-256. */
class RaptorQFileReceiver(
    private val manifest: OptiFrameManifest,
    decoderFactory: RaptorQBlockDecoderFactory,
    private val spoolFile: File? = null
) {
    private val blockReceivers: List<RaptorQBlockReceiver>
    private val originalByteCounts: IntArray
    private val blockFileOffsets: LongArray
    private val spooledBlocks: BooleanArray
    private var verifiedFile: ByteArray? = null
    private var verifiedToStream = false
    private var spoolRandomAccessFile: RandomAccessFile? = null
    private var verifiedSpooledFile: File? = null

    init {
        require(manifest.sourceSymbolBytes == ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES) {
            "Manifest source symbol size does not match OF8"
        }
        if (spoolFile == null) {
            require(manifest.fileSize <= Int.MAX_VALUE.toLong()) {
                "In-memory file reconstruction is limited to ${Int.MAX_VALUE} bytes"
            }
        }

        val blockKValues = manifest.sourceBlockKValues
        originalByteCounts = originalByteCounts(
            fileSize = manifest.fileSize,
            sourceBlockKValues = blockKValues,
            sourceSymbolBytes = manifest.sourceSymbolBytes
        )
        blockFileOffsets = LongArray(originalByteCounts.size)
        var fileOffset = 0L
        originalByteCounts.forEachIndexed { blockNumber, byteCount ->
            blockFileOffsets[blockNumber] = fileOffset
            fileOffset += byteCount
        }
        check(fileOffset == manifest.fileSize) {
            "Decoded source blocks do not cover the declared file size"
        }
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
        spooledBlocks = BooleanArray(blockReceivers.size)
        spoolFile?.let { file ->
            file.parentFile?.mkdirs()
            spoolRandomAccessFile = RandomAccessFile(file, "rw").apply { setLength(0L) }
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

    val decodedBlocksInMemory: Int
        get() = blockReceivers.count { it.decodedSourceBlock != null }

    internal var spoolWriteFailureForTest: (() -> Unit)? = null

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
        val blockNumber = item.sourceBlockNumber
        val blockReceiver = blockReceivers[blockNumber]
        val result = blockReceiver.accept(item)
        if (spoolFile != null && blockReceiver.isComplete && !spooledBlocks[blockNumber]) {
            spoolDecodedBlock(blockNumber, checkNotNull(blockReceiver.decodedSourceBlock))
            spooledBlocks[blockNumber] = true
            blockReceiver.releaseDecodedData()
        }
        return result
    }

    /** Returns the verified file bytes; no data is returned before all checks pass. */
    fun finishAndVerify(): ByteArray {
        check(spoolFile == null) { "Spooling receiver requires finishAndVerifySpooledFile" }
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
        check(spoolFile == null) { "Spooling receiver requires finishAndVerifySpooledFile" }
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

    fun finishAndVerifySpooledFile(): File {
        val file = checkNotNull(spoolFile) { "Spooling is not configured" }
        verifiedSpooledFile?.let { return it }
        check(isComplete) { "Not all source blocks have been decoded" }
        blockReceivers.forEachIndexed { blockNumber, receiver ->
            if (receiver.isComplete && !spooledBlocks[blockNumber]) {
                spoolDecodedBlock(blockNumber, checkNotNull(receiver.decodedSourceBlock))
                spooledBlocks[blockNumber] = true
                receiver.releaseDecodedData()
            }
        }
        val randomAccessFile = checkNotNull(spoolRandomAccessFile) {
            "Spool file is already closed"
        }
        try {
            randomAccessFile.setLength(manifest.fileSize)
        } finally {
            close()
        }

        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        if (!digest.digest().contentEquals(manifest.fileSha256)) {
            file.delete()
            throw IllegalStateException("Reconstructed file SHA-256 does not match the manifest")
        }

        verifiedSpooledFile = file
        return file
    }

    fun close() {
        val randomAccessFile = spoolRandomAccessFile ?: return
        spoolRandomAccessFile = null
        randomAccessFile.close()
    }

    private fun spoolDecodedBlock(blockNumber: Int, decoded: RaptorQDecodedSourceBlock) {
        spoolWriteFailureForTest?.invoke()
        require(decoded.sourceBlockNumber == blockNumber)
        require(decoded.k == manifest.sourceBlockKValues[blockNumber]) {
            "Decoded K for source block $blockNumber does not match the manifest"
        }
        var blockBytesRemaining = originalByteCounts[blockNumber]
        val file = checkNotNull(spoolRandomAccessFile) { "Spool file is already closed" }
        file.seek(blockFileOffsets[blockNumber])
        for (symbol in decoded.sourceSymbols) {
            if (blockBytesRemaining == 0) break
            val bytes = symbol.bytes
            val bytesToWrite = minOf(bytes.size, blockBytesRemaining)
            file.write(bytes, 0, bytesToWrite)
            blockBytesRemaining -= bytesToWrite
        }
        check(blockBytesRemaining == 0) {
            "Decoded source symbols do not cover source block $blockNumber"
        }
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
