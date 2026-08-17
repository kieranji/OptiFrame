package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class OptiFrameManifestTest {

    @Test
    fun manifestFor257ByteFileIsCorrect() {

        val file =
            createData(257)

        val blocks =
            Of8FileSymbolizer.split(file)

        val manifest =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "example.bin",
                fileBytes = file,
                sourceBlocks = blocks,
                mimeType =
                    "application/octet-stream"
            )

        assertEquals(
            "example.bin",
            manifest.fileName
        )

        assertEquals(
            257L,
            manifest.fileSize
        )

        assertEquals(
            256,
            manifest.sourceSymbolBytes
        )

        assertEquals(
            1,
            manifest.sourceBlockCount
        )

        assertContentEquals(
            intArrayOf(2),
            manifest.sourceBlockKValues
        )

        assertContentEquals(
            Sha256.digest(file),
            manifest.fileSha256
        )

        assertEquals(
            Sha256.hex(file),
            manifest.fileSha256Hex()
        )
    }

    @Test
    fun oneMiBManifestHasFourBlocks() {

        val file =
            createData(
                1024 * 1024
            )

        val blocks =
            Of8FileSymbolizer.split(file)

        val manifest =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "one-mib.bin",
                fileBytes = file,
                sourceBlocks = blocks
            )

        assertEquals(
            4,
            manifest.sourceBlockCount
        )

        assertContentEquals(
            intArrayOf(
                1024,
                1024,
                1024,
                1024
            ),
            manifest.sourceBlockKValues
        )
    }

    @Test
    fun emptyFileManifestWorks() {

        val file =
            ByteArray(0)

        val blocks =
            Of8FileSymbolizer.split(file)

        val manifest =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "empty.bin",
                fileBytes = file,
                sourceBlocks = blocks
            )

        assertEquals(
            0L,
            manifest.fileSize
        )

        assertEquals(
            0,
            manifest.sourceBlockCount
        )

        assertContentEquals(
            intArrayOf(),
            manifest.sourceBlockKValues
        )

        assertEquals(
            Sha256.hex(file),
            manifest.fileSha256Hex()
        )
    }

    @Test
    fun optionalMetadataIsPreserved() {

        val file =
            createData(100)

        val blocks =
            Of8FileSymbolizer.split(file)

        val manifest =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "photo.jpg",
                fileBytes = file,
                sourceBlocks = blocks,
                createdAtUnixMillis =
                    1_800_000_000_000L,
                mimeType =
                    "image/jpeg"
            )

        assertEquals(
            1_800_000_000_000L,
            manifest.createdAtUnixMillis
        )

        assertEquals(
            "image/jpeg",
            manifest.mimeType
        )
    }

    private fun createData(
        size: Int
    ): ByteArray {

        return ByteArray(size) { index ->

            (
                    (
                            index.toLong() *
                                    131L +
                                    47L
                            ) and 0xFF
                    )
                .toByte()
        }
    }
}