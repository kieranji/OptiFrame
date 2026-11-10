package com.kieran.optiframe.protocol

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RaptorQSpooledReceiverTest {
    @Test
    fun multiBlockFileSpoolsAndReleasesEachDecodedBlock() {
        val original = multiBlockFile()
        val manifest = manifestFor(original)
        val spoolFile = File.createTempFile("optiframe-spool", ".part")
        val receiver = RaptorQFileReceiver(manifest, OpenRqRaptorQ.decoderFactory, spoolFile)
        try {
            val specs = Of8SourceBlockSpec.fromFileSize(original.size.toLong())
            assertTrue(specs.size >= 3)
            feedBlocks(receiver, original, 0 until specs.size)

            assertTrue(receiver.isComplete)
            assertEquals(0, receiver.decodedBlocksInMemory)
            val verifiedFile = receiver.finishAndVerifySpooledFile()
            assertEquals(spoolFile, verifiedFile)
            assertEquals(spoolFile, receiver.finishAndVerifySpooledFile())
            assertContentEquals(original, verifiedFile.readBytes())
            assertContentEquals(manifest.fileSha256, sha256(verifiedFile.readBytes()))
        } finally {
            receiver.close()
            spoolFile.delete()
        }
    }

    @Test
    fun blocksCanBeSpooledOutOfOrder() {
        val original = multiBlockFile()
        val manifest = manifestFor(original)
        val blockCount = Of8SourceBlockSpec.fromFileSize(original.size.toLong()).size
        val spoolFile = File.createTempFile("optiframe-order", ".part")
        val receiver = RaptorQFileReceiver(manifest, OpenRqRaptorQ.decoderFactory, spoolFile)
        try {
            feedBlocks(receiver, original, (blockCount - 1 downTo 0))

            assertTrue(receiver.isComplete)
            assertEquals(0, receiver.decodedBlocksInMemory)
            assertContentEquals(original, receiver.finishAndVerifySpooledFile().readBytes())
        } finally {
            receiver.close()
            spoolFile.delete()
        }
    }

    @Test
    fun wrongFileDigestDeletesTheSpoolFile() {
        val original = multiBlockFile()
        val badDigest = sha256(original).also { it[0] = (it[0].toInt() xor 1).toByte() }
        val manifest = manifestFor(original, badDigest)
        val spoolFile = File.createTempFile("optiframe-badhash", ".part")
        val receiver = RaptorQFileReceiver(manifest, OpenRqRaptorQ.decoderFactory, spoolFile)
        try {
            feedBlocks(
                receiver,
                original,
                Of8SourceBlockSpec.fromFileSize(original.size.toLong()).indices
            )

            val error = assertFailsWith<IllegalStateException> {
                receiver.finishAndVerifySpooledFile()
            }
            assertEquals("Reconstructed file SHA-256 does not match the manifest", error.message)
            assertFalse(spoolFile.exists())
        } finally {
            receiver.close()
            spoolFile.delete()
        }
    }

    @Test
    fun emptyFileVerifiesToAnEmptySpoolFile() {
        val original = ByteArray(0)
        val manifest = manifestFor(original)
        val spoolFile = File.createTempFile("optiframe-empty", ".part")
        val receiver = RaptorQFileReceiver(manifest, OpenRqRaptorQ.decoderFactory, spoolFile)
        try {
            val verifiedFile = receiver.finishAndVerifySpooledFile()

            assertTrue(receiver.isComplete)
            assertEquals(0L, verifiedFile.length())
            assertContentEquals(manifest.fileSha256, sha256(verifiedFile.readBytes()))
        } finally {
            receiver.close()
            spoolFile.delete()
        }
    }

    @Test
    fun inMemoryFinishIsRejectedWhenSpoolingIsConfigured() {
        val original = ByteArray(0)
        val spoolFile = File.createTempFile("optiframe-finish", ".part")
        val receiver = RaptorQFileReceiver(
            manifestFor(original),
            OpenRqRaptorQ.decoderFactory,
            spoolFile
        )
        try {
            assertFailsWith<IllegalStateException> { receiver.finishAndVerify() }
        } finally {
            receiver.close()
            spoolFile.delete()
        }
    }

    private fun feedBlocks(
        receiver: RaptorQFileReceiver,
        original: ByteArray,
        blockNumbers: IntProgression
    ) {
        val blocks = Of8FileSymbolizer.split(original)
        for (blockNumber in blockNumbers) {
            val block = blocks[blockNumber]
            val encoder = OpenRqRaptorQ.encoder(block)
            try {
                for (esi in 0 until block.k) {
                    val result = receiver.accept(
                        RaptorQTransmissionItem(
                            sourceBlockNumber = blockNumber,
                            symbol = encoder.encodeSymbol(esi.toLong())
                        )
                    )
                    if (result.disposition == RaptorQBlockReceiveResult.Disposition.BLOCK_COMPLETE) {
                        assertEquals(0, receiver.decodedBlocksInMemory)
                    }
                }
            } finally {
                encoder.close()
            }
        }
    }

    private fun multiBlockFile(): ByteArray =
        ByteArray(ProtocolConstants.OF8_SOURCE_BLOCK_MAX_BYTES * 2 + 137) { index ->
            (index * 31 + 7).toByte()
        }

    private fun manifestFor(
        fileBytes: ByteArray,
        fileSha256: ByteArray = sha256(fileBytes)
    ): OptiFrameManifest {
        val specs = Of8SourceBlockSpec.fromFileSize(fileBytes.size.toLong())
        return OptiFrameManifest(
            fileName = "spooled.bin",
            fileSize = fileBytes.size.toLong(),
            fileSha256 = fileSha256,
            sourceSymbolBytes = ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES,
            sourceBlockKValues = specs.map { it.k }.toIntArray()
        )
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
