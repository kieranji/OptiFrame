package com.kieran.optiframe.protocol

object OptiFrameBootstrapBuilder {

    fun build(
        sessionId: Long,
        fileBytes: ByteArray,
        manifest: OptiFrameManifest,
        capabilityFlags: Long,
        controlEndpoint: ByteArray =
            ByteArray(0)
    ): OptiFrameBootstrapPayload {

        require(
            manifest.fileSize ==
                    fileBytes.size.toLong()
        ) {
            "Manifest file size does not match actual file"
        }

        val actualFileHash =
            Sha256.digest(
                fileBytes
            )

        require(
            actualFileHash.contentEquals(
                manifest.fileSha256
            )
        ) {
            "Manifest SHA-256 does not match actual file"
        }

        return OptiFrameBootstrapPayload(
            sessionId = sessionId,
            fileSize =
                fileBytes.size.toULong(),
            fileSha256 =
                actualFileHash,
            manifestHash =
                OptiFrameManifestCodec.hash(
                    manifest
                ),
            capabilityFlags =
                capabilityFlags,
            controlEndpoint =
                controlEndpoint
        )
    }
}