package com.kieran.optiframe.protocol

object OptiFrameManifestCodec {

    fun encode(
        manifest: OptiFrameManifest
    ): ByteArray {

        val fileNameBytes =
            manifest.fileName.encodeToByteArray()

        require(
            fileNameBytes.size <=
                    ProtocolConstants.MAX_MANIFEST_STRING_BYTES
        ) {
            "UTF-8 filename is too long"
        }

        val mimeBytes =
            manifest.mimeType
                ?.encodeToByteArray()
                ?: ByteArray(0)

        require(
            mimeBytes.size <=
                    ProtocolConstants.MAX_MANIFEST_STRING_BYTES
        ) {
            "UTF-8 MIME type is too long"
        }

        val hasCreatedAt =
            manifest.createdAtUnixMillis != null

        val hasMime =
            manifest.mimeType != null

        var flags = 0

        if (hasCreatedAt) {
            flags =
                flags or
                        ProtocolConstants
                            .MANIFEST_FLAG_CREATED_AT
        }

        if (hasMime) {
            flags =
                flags or
                        ProtocolConstants
                            .MANIFEST_FLAG_MIME_TYPE
        }

        val optionalCreatedAtBytes =
            if (hasCreatedAt) 8 else 0

        val totalSize =
            4 +     // magic
                    1 + // version
                    1 + // flags
                    2 + // filename length
                    2 + // MIME length
                    2 + // T
                    4 + // block count
                    8 + // file size
                    32 + // SHA-256
                    optionalCreatedAtBytes +
                    fileNameBytes.size +
                    mimeBytes.size +
                    manifest.sourceBlockCount * 2

        val output =
            ByteArray(totalSize)

        var offset = 0

        ProtocolConstants.MANIFEST_MAGIC
            .copyInto(
                destination = output,
                destinationOffset = offset
            )

        offset += 4

        output[offset++] =
            ProtocolConstants
                .MANIFEST_VERSION
                .toByte()

        output[offset++] =
            flags.toByte()

        BinaryIo.writeUInt16BE(
            fileNameBytes.size
        ).copyInto(output, offset)

        offset += 2

        BinaryIo.writeUInt16BE(
            mimeBytes.size
        ).copyInto(output, offset)

        offset += 2

        BinaryIo.writeUInt16BE(
            manifest.sourceSymbolBytes
        ).copyInto(output, offset)

        offset += 2

        BinaryIo.writeUInt32BE(
            manifest.sourceBlockCount.toLong()
        ).copyInto(output, offset)

        offset += 4

        BinaryIo.writeUInt64BE(
            manifest.fileSize.toULong()
        ).copyInto(output, offset)

        offset += 8

        manifest.fileSha256.copyInto(
            destination = output,
            destinationOffset = offset
        )

        offset += 32

        if (hasCreatedAt) {

            BinaryIo.writeUInt64BE(
                manifest
                    .createdAtUnixMillis!!
                    .toULong()
            ).copyInto(output, offset)

            offset += 8
        }

        fileNameBytes.copyInto(
            destination = output,
            destinationOffset = offset
        )

        offset +=
            fileNameBytes.size

        mimeBytes.copyInto(
            destination = output,
            destinationOffset = offset
        )

        offset +=
            mimeBytes.size

        for (
        k in
        manifest.sourceBlockKValues
        ) {

            BinaryIo.writeUInt16BE(k)
                .copyInto(
                    destination = output,
                    destinationOffset = offset
                )

            offset += 2
        }

        check(offset == output.size)

        return output
    }

    fun decode(
        encoded: ByteArray
    ): OptiFrameManifest {

        require(encoded.size >= 56) {
            "Manifest is too short"
        }

        var offset = 0

        val magic =
            encoded.copyOfRange(
                0,
                4
            )

        require(
            magic.contentEquals(
                ProtocolConstants.MANIFEST_MAGIC
            )
        ) {
            "Invalid manifest magic"
        }

        offset += 4

        val version =
            encoded[offset++].toInt() and 0xFF

        require(
            version ==
                    ProtocolConstants.MANIFEST_VERSION
        ) {
            "Unsupported manifest version: $version"
        }

        val flags =
            encoded[offset++].toInt() and 0xFF

        require(
            flags and
                    ProtocolConstants
                        .MANIFEST_KNOWN_FLAGS_MASK.inv() ==
                    0
        ) {
            "Unknown manifest flags: 0x${flags.toString(16)}"
        }

        val fileNameLength =
            BinaryIo.readUInt16BE(
                encoded,
                offset
            )

        offset += 2

        val mimeLength =
            BinaryIo.readUInt16BE(
                encoded,
                offset
            )

        offset += 2

        val sourceSymbolBytes =
            BinaryIo.readUInt16BE(
                encoded,
                offset
            )

        offset += 2

        val blockCountLong =
            BinaryIo.readUInt32BE(
                encoded,
                offset
            )

        offset += 4

        require(
            blockCountLong <=
                    Int.MAX_VALUE.toLong()
        ) {
            "Source block count too large"
        }

        val blockCount =
            blockCountLong.toInt()

        val fileSizeUnsigned =
            BinaryIo.readUInt64BE(
                encoded,
                offset
            )

        offset += 8

        require(
            fileSizeUnsigned <=
                    Long.MAX_VALUE.toULong()
        ) {
            "File size exceeds current JVM implementation range"
        }

        val fileSize =
            fileSizeUnsigned.toLong()

        require(
            offset + 32 <= encoded.size
        )

        val fileSha256 =
            encoded.copyOfRange(
                offset,
                offset + 32
            )

        offset += 32

        val hasCreatedAt =
            flags and
                    ProtocolConstants
                        .MANIFEST_FLAG_CREATED_AT !=
                    0

        val hasMime =
            flags and
                    ProtocolConstants
                        .MANIFEST_FLAG_MIME_TYPE !=
                    0

        val createdAt =
            if (hasCreatedAt) {

                require(
                    offset + 8 <=
                            encoded.size
                )

                val value =
                    BinaryIo.readUInt64BE(
                        encoded,
                        offset
                    )

                offset += 8

                require(
                    value <=
                            Long.MAX_VALUE.toULong()
                )

                value.toLong()

            } else {
                null
            }

        if (!hasMime) {

            require(mimeLength == 0) {
                "MIME length must be zero when MIME flag is absent"
            }
        }

        val remainingRequired =
            fileNameLength +
                    mimeLength +
                    blockCount * 2

        require(
            offset +
                    remainingRequired ==
                    encoded.size
        ) {
            "Manifest length does not match encoded fields"
        }

        val fileNameBytes =
            encoded.copyOfRange(
                offset,
                offset + fileNameLength
            )

        offset +=
            fileNameLength

        val mimeBytes =
            encoded.copyOfRange(
                offset,
                offset + mimeLength
            )

        offset +=
            mimeLength

        val kValues =
            IntArray(blockCount)

        for (i in 0 until blockCount) {

            kValues[i] =
                BinaryIo.readUInt16BE(
                    encoded,
                    offset
                )

            offset += 2
        }

        check(offset == encoded.size)

        return OptiFrameManifest(
            fileName =
                fileNameBytes.decodeToString(),
            fileSize =
                fileSize,
            fileSha256 =
                fileSha256,
            sourceSymbolBytes =
                sourceSymbolBytes,
            sourceBlockKValues =
                kValues,
            createdAtUnixMillis =
                createdAt,
            mimeType =
                if (hasMime) {
                    mimeBytes.decodeToString()
                } else {
                    null
                }
        )
    }

    fun hash(
        manifest: OptiFrameManifest
    ): ByteArray {

        return Sha256.digest(
            encode(manifest)
        )
    }

    fun hashHex(
        manifest: OptiFrameManifest
    ): String {

        return hash(manifest)
            .joinToString("") {
                "%02x".format(
                    it.toInt() and 0xFF
                )
            }
    }
}