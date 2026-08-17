package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OptiFrameBootstrapCodecTest {

    @Test
    fun bootstrapRoundTripPreservesFields() {

        val file =
            "OptiFrame bootstrap test"
                .encodeToByteArray()

        val blocks =
            Of8FileSymbolizer.split(
                file
            )

        val manifest =
            OptiFrameManifestBuilder.buildOf8(
                fileName = "test.bin",
                fileBytes = file,
                sourceBlocks = blocks
            )

        val original =
            OptiFrameBootstrapBuilder.build(
                sessionId =
                    0x12345678L,
                fileBytes =
                    file,
                manifest =
                    manifest,
                capabilityFlags =
                    0x00000005L,
                controlEndpoint =
                    byteArrayOf(
                        0x01,
                        0x02,
                        0x03
                    )
            )

        val encoded =
            OptiFrameBootstrapCodec.encode(
                original
            )

        val recovered =
            OptiFrameBootstrapCodec.decode(
                encoded
            )

        assertEquals(
            original.sessionId,
            recovered.sessionId
        )

        assertEquals(
            original.fileSize,
            recovered.fileSize
        )

        assertContentEquals(
            original.fileSha256,
            recovered.fileSha256
        )

        assertContentEquals(
            original.manifestHash,
            recovered.manifestHash
        )

        assertEquals(
            original.capabilityFlags,
            recovered.capabilityFlags
        )

        assertContentEquals(
            original.controlEndpoint,
            recovered.controlEndpoint
        )
    }

    @Test
    fun canonicalBootstrapVectorMatches() {

        val file =
            "abc".encodeToByteArray()

        val blocks =
            Of8FileSymbolizer.split(
                file
            )

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

        val payload =
            OptiFrameBootstrapBuilder.build(
                sessionId =
                    0x12345678L,
                fileBytes =
                    file,
                manifest =
                    manifest,
                capabilityFlags =
                    0x00000005L,
                controlEndpoint =
                    byteArrayOf(
                        0x01,
                        0x02,
                        0x03
                    )
            )

        val encoded =
            OptiFrameBootstrapCodec.encode(
                payload
            )

        assertEquals(
            "4f465631" +
                    "01" +
                    "12345678" +
                    "0000000000000003" +
                    "ba7816bf8f01cfea414140de5dae2223" +
                    "b00361a396177a9cb410ff61f20015ad" +
                    "4f4a347a07fdd21b276eb5ab47f85703" +
                    "b1b34db249a8a3001c4022e6622fa0b1" +
                    "00000005" +
                    "0003" +
                    "010203",
            toHex(encoded)
        )
    }

    @Test
    fun noControlEndpointUsesZeroLength() {

        val payload =
            OptiFrameBootstrapPayload(
                sessionId = 1,
                fileSize = 0u,
                fileSha256 =
                    ByteArray(32),
                manifestHash =
                    ByteArray(32),
                capabilityFlags = 0
            )

        val encoded =
            OptiFrameBootstrapCodec.encode(
                payload
            )

        assertEquals(
            ProtocolConstants
                .BOOTSTRAP_FIXED_BYTES,
            encoded.size
        )

        val recovered =
            OptiFrameBootstrapCodec.decode(
                encoded
            )

        assertEquals(
            0,
            recovered.controlEndpoint.size
        )
    }

    @Test
    fun invalidMagicIsRejected() {

        val payload =
            OptiFrameBootstrapPayload(
                sessionId = 1,
                fileSize = 0u,
                fileSha256 =
                    ByteArray(32),
                manifestHash =
                    ByteArray(32),
                capabilityFlags = 0
            )

        val encoded =
            OptiFrameBootstrapCodec
                .encode(payload)

        encoded[0] =
            0x00

        assertFailsWith<IllegalArgumentException> {
            OptiFrameBootstrapCodec.decode(
                encoded
            )
        }
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