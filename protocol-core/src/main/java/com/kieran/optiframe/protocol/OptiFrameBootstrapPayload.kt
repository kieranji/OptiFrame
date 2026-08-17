package com.kieran.optiframe.protocol

class OptiFrameBootstrapPayload(
    val sessionId: Long,
    val fileSize: ULong,
    fileSha256: ByteArray,
    manifestHash: ByteArray,
    val capabilityFlags: Long,
    controlEndpoint: ByteArray = ByteArray(0)
) {

    val fileSha256: ByteArray =
        fileSha256.copyOf()

    val manifestHash: ByteArray =
        manifestHash.copyOf()

    val controlEndpoint: ByteArray =
        controlEndpoint.copyOf()

    init {
        require(
            sessionId in 0..0xFFFF_FFFFL
        ) {
            "sessionId must fit uint32"
        }

        require(
            this.fileSha256.size == 32
        ) {
            "fileSha256 must contain exactly 32 bytes"
        }

        require(
            this.manifestHash.size == 32
        ) {
            "manifestHash must contain exactly 32 bytes"
        }

        require(
            capabilityFlags in 0..0xFFFF_FFFFL
        ) {
            "capabilityFlags must fit uint32"
        }

        require(
            this.controlEndpoint.size <=
                    ProtocolConstants.MAX_CONTROL_ENDPOINT_BYTES
        ) {
            "controlEndpoint is too long"
        }
    }
}