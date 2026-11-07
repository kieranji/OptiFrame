package com.kieran.optiframe.protocol

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** File-level sender setup: manifest plus an injected-backend feedback controller. */
class RaptorQFileSenderSession private constructor(
    val manifest: OptiFrameManifest,
    val controller: RaptorQFileSendController
) {
    val progress: RaptorQFileSendProgress
        get() = controller.progress

    fun nextTransmission(): RaptorQTransmissionItem? = controller.nextTransmission()

    fun prepareNextBlock() = controller.prepareNextBlock()

    fun applyFeedback(feedback: RaptorQBlockFeedback) =
        controller.applyFeedback(feedback)

    companion object {
        fun fromFile(
            fileName: String,
            file: File,
            initialRepairSymbolsPerBlock: Int,
            encoderFactory: (Of8SourceBlockSpec) -> RaptorQBlockEncoder,
            createdAtUnixMillis: Long? = null,
            mimeType: String? = null,
            advanceAfterRepairQueue: Boolean = false,
            initialRepairFraction: Double? = null
        ): RaptorQFileSenderSession {
            require(file.isFile && file.length() >= 0L)
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val specs = Of8SourceBlockSpec.fromFileSize(file.length())
            val manifest = OptiFrameManifest(
                fileName = fileName,
                fileSize = file.length(),
                fileSha256 = digest.digest(),
                sourceSymbolBytes = ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES,
                sourceBlockKValues = specs.map { it.k }.toIntArray(),
                createdAtUnixMillis = createdAtUnixMillis,
                mimeType = mimeType
            )
            val scheduler = RaptorQFileTransmissionScheduler.fromSpecs(
                specs, initialRepairSymbolsPerBlock, encoderFactory,
                advanceAfterRepairQueue, initialRepairFraction
            )
            return RaptorQFileSenderSession(manifest, RaptorQFileSendController(scheduler))
        }

        fun fromBytes(
            fileName: String,
            fileBytes: ByteArray,
            initialRepairSymbolsPerBlock: Int,
            encoderFactory: (Of8SourceBlock) -> RaptorQBlockEncoder,
            createdAtUnixMillis: Long? = null,
            mimeType: String? = null,
            advanceAfterRepairQueue: Boolean = false,
            initialRepairFraction: Double? = null
        ): RaptorQFileSenderSession {
            val blocks = Of8FileSymbolizer.split(fileBytes)
            val manifest = OptiFrameManifest(
                fileName = fileName,
                fileSize = fileBytes.size.toLong(),
                fileSha256 = Sha256.digest(fileBytes),
                sourceSymbolBytes = ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES,
                sourceBlockKValues = blocks.map { it.k }.toIntArray(),
                createdAtUnixMillis = createdAtUnixMillis,
                mimeType = mimeType
            )
            val scheduler = RaptorQFileTransmissionScheduler.fromBlocks(
                blocks = blocks,
                initialRepairSymbolsPerBlock = initialRepairSymbolsPerBlock,
                encoderFactory = encoderFactory,
                advanceAfterRepairQueue = advanceAfterRepairQueue,
                initialRepairFraction = initialRepairFraction
            )
            return RaptorQFileSenderSession(
                manifest = manifest,
                controller = RaptorQFileSendController(scheduler)
            )
        }
    }
}
