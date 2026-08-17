package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class OptiFrameManifestCodecTest {

    @Test
    fun manifestRoundTripPreservesFields() {

        val file =
            ByteArray(257) {
                ((it * 73 + 19) and 0xFF)
                    .toByte()
            }

        val blocks =
            Of8FileSymbolizer.split(file)

        val original =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "测试-file.bin",
                fileBytes = file,
                sourceBlocks = blocks,
                createdAtUnixMillis =
                    1_800_000_000_000L,
                mimeType =
                    "application/octet-stream"
            )

        val encoded =
            OptiFrameManifestCodec.encode(
                original
            )

        val recovered =
            OptiFrameManifestCodec.decode(
                encoded
            )

        assertEquals(
            original.fileName,
            recovered.fileName
        )

        assertEquals(
            original.fileSize,
            recovered.fileSize
        )

        assertContentEquals(
            original.fileSha256,
            recovered.fileSha256
        )

        assertEquals(
            original.sourceSymbolBytes,
            recovered.sourceSymbolBytes
        )

        assertContentEquals(
            original.sourceBlockKValues,
            recovered.sourceBlockKValues
        )

        assertEquals(
            original.createdAtUnixMillis,
            recovered.createdAtUnixMillis
        )

        assertEquals(
            original.mimeType,
            recovered.mimeType
        )
    }

    @Test
    fun canonicalKnownVectorMatches() {

        val file =
            "abc".encodeToByteArray()

        val blocks =
            Of8FileSymbolizer.split(file)

        val manifest =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "a.bin",
                fileBytes = file,
                sourceBlocks = blocks,
                createdAtUnixMillis =
                    1_800_000_000_000L,
                mimeType =
                    "application/octet-stream"
            )

        val encoded =
            OptiFrameManifestCodec.encode(
                manifest
            )

        assertEquals(
            "4f464d31010300050018010000000001" +
                    "0000000000000003" +
                    "ba7816bf8f01cfea414140de5dae2223" +
                    "b00361a396177a9cb410ff61f20015ad" +
                    "000001a3185c5000" +
                    "612e62696e" +
                    "6170706c69636174696f6e2f6f637465742d73747265616d" +
                    "0001",
            toHex(encoded)
        )
    }

    @Test
    fun canonicalManifestHashMatches() {

        val file =
            "abc".encodeToByteArray()

        val blocks =
            Of8FileSymbolizer.split(file)

        val manifest =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "a.bin",
                fileBytes = file,
                sourceBlocks = blocks,
                createdAtUnixMillis =
                    1_800_000_000_000L,
                mimeType =
                    "application/octet-stream"
            )

        assertEquals(
            "4f4a347a07fdd21b276eb5ab47f85703" +
                    "b1b34db249a8a3001c4022e6622fa0b1",
            OptiFrameManifestCodec.hashHex(
                manifest
            )
        )
    }

    @Test
    fun emptyFileManifestRoundTrip() {

        val file =
            ByteArray(0)

        val manifest =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "empty.bin",
                fileBytes = file,
                sourceBlocks =
                    Of8FileSymbolizer.split(file)
            )

        val encoded =
            OptiFrameManifestCodec.encode(
                manifest
            )

        val decoded =
            OptiFrameManifestCodec.decode(
                encoded
            )

        assertEquals(
            0L,
            decoded.fileSize
        )

        assertEquals(
            0,
            decoded.sourceBlockCount
        )

        assertEquals(
            null,
            decoded.mimeType
        )
    }

    private fun toHex(
        bytes: ByteArray
    ): String {

        return bytes.joinToString("") {
            "%02x".format(
                it.toInt() and 0xFF
            )
        }
    }
}