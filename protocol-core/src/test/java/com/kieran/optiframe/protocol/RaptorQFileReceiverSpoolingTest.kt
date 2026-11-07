package com.kieran.optiframe.protocol

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RaptorQFileReceiverSpoolingTest {
    @Test
    fun failedBlockWriteCanBeRetriedOnTheNextSymbol() {
        val original = byteArrayOf(3, 9, 17, 28, 41)
        val sourceSymbol = ByteArray(ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES)
        original.copyInto(sourceSymbol)
        val manifest = OptiFrameManifest(
            fileName = "retry-spool.bin",
            fileSize = original.size.toLong(),
            fileSha256 = MessageDigest.getInstance("SHA-256").digest(original),
            sourceSymbolBytes = ProtocolConstants.OF8_SOURCE_SYMBOL_BYTES,
            sourceBlockKValues = intArrayOf(1)
        )
        val spoolFile = File.createTempFile("optiframe-retry", ".part")
        val receiver = RaptorQFileReceiver(
            manifest = manifest,
            decoderFactory = RaptorQBlockDecoderFactory { _, _ ->
                SingleSourceSymbolDecoder()
            },
            spoolFile = spoolFile
        )
        var failNextWrite = true
        receiver.spoolWriteFailureForTest = {
            if (failNextWrite) {
                failNextWrite = false
                throw IOException("simulated spool write failure")
            }
        }
        val item = RaptorQTransmissionItem(
            sourceBlockNumber = 0,
            symbol = RaptorQSymbol(
                esi = 0,
                bytes = sourceSymbol,
                kind = RaptorQSymbol.Kind.SOURCE
            )
        )

        try {
            assertFailsWith<IOException> { receiver.accept(item) }
            assertEquals(1, receiver.decodedBlocksInMemory)

            val retryResult = receiver.accept(item)

            assertEquals(RaptorQBlockReceiveResult.Disposition.ALREADY_COMPLETE, retryResult.disposition)
            assertEquals(0, receiver.decodedBlocksInMemory)
            assertContentEquals(original, receiver.finishAndVerifySpooledFile().readBytes())
        } finally {
            receiver.close()
            spoolFile.delete()
        }
    }

    private class SingleSourceSymbolDecoder : RaptorQBlockDecoder {
        override val sourceSymbolCount: Int = 1
        private var sourceSymbol: ByteArray? = null

        override fun addSymbol(symbol: RaptorQSymbol): Boolean {
            if (sourceSymbol != null) return false
            sourceSymbol = symbol.bytes
            return true
        }

        override fun decodeSourceSymbols(): List<ByteArray>? =
            sourceSymbol?.let { listOf(it) }
    }
}
