package com.kieran.optiframe.protocol

object OptiFrameBootstrapCodec {

    fun encode(
        payload: OptiFrameBootstrapPayload
    ): ByteArray {

        val endpoint =
            payload.controlEndpoint

        val output =
            ByteArray(
                ProtocolConstants.BOOTSTRAP_FIXED_BYTES +
                        endpoint.size
            )

        var offset = 0

        ProtocolConstants
            .BOOTSTRAP_MAGIC
            .copyInto(
                destination = output,
                destinationOffset = offset
            )

        offset += 4

        output[offset++] =
            ProtocolConstants
                .PROTOCOL_VERSION
                .toByte()

        BinaryIo.writeUInt32BE(
            payload.sessionId
        ).copyInto(output, offset)

        offset += 4

        BinaryIo.writeUInt64BE(
            payload.fileSize
        ).copyInto(output, offset)

        offset += 8

        payload.fileSha256.copyInto(
            destination = output,
            destinationOffset = offset
        )

        offset += 32

        payload.manifestHash.copyInto(
            destination = output,
            destinationOffset = offset
        )

        offset += 32

        BinaryIo.writeUInt32BE(
            payload.capabilityFlags
        ).copyInto(output, offset)

        offset += 4

        BinaryIo.writeUInt16BE(
            endpoint.size
        ).copyInto(output, offset)

        offset += 2

        endpoint.copyInto(
            destination = output,
            destinationOffset = offset
        )

        offset += endpoint.size

        check(offset == output.size)

        return output
    }

    fun decode(
        encoded: ByteArray
    ): OptiFrameBootstrapPayload {

        require(
            encoded.size >=
                    ProtocolConstants.BOOTSTRAP_FIXED_BYTES
        ) {
            "Bootstrap payload is too short"
        }

        var offset = 0

        val magic =
            encoded.copyOfRange(
                offset,
                offset + 4
            )

        require(
            magic.contentEquals(
                ProtocolConstants.BOOTSTRAP_MAGIC
            )
        ) {
            "Invalid bootstrap magic"
        }

        offset += 4

        val version =
            encoded[offset++]
                .toInt() and 0xFF

        require(
            version ==
                    ProtocolConstants.PROTOCOL_VERSION
        ) {
            "Unsupported protocol version: $version"
        }

        val sessionId =
            BinaryIo.readUInt32BE(
                encoded,
                offset
            )

        offset += 4

        val fileSize =
            BinaryIo.readUInt64BE(
                encoded,
                offset
            )

        offset += 8

        val fileSha256 =
            encoded.copyOfRange(
                offset,
                offset + 32
            )

        offset += 32

        val manifestHash =
            encoded.copyOfRange(
                offset,
                offset + 32
            )

        offset += 32

        val capabilityFlags =
            BinaryIo.readUInt32BE(
                encoded,
                offset
            )

        offset += 4

        val endpointLength =
            BinaryIo.readUInt16BE(
                encoded,
                offset
            )

        offset += 2

        require(
            offset + endpointLength ==
                    encoded.size
        ) {
            "Bootstrap endpoint length does not match payload size"
        }

        val endpoint =
            encoded.copyOfRange(
                offset,
                offset + endpointLength
            )

        return OptiFrameBootstrapPayload(
            sessionId = sessionId,
            fileSize = fileSize,
            fileSha256 = fileSha256,
            manifestHash = manifestHash,
            capabilityFlags =
                capabilityFlags,
            controlEndpoint =
                endpoint
        )
    }
}