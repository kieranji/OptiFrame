package com.kieran.optiframe.protocol

data class OptiFrameGlobalHeader(
    val profileId: Int,
    val sessionId: Long,
    val frameSequence: Long,
    val sourceBlockNumber: Int,
    val paletteId: Int,
    val frameType: Int,
    val flags: Int,
    val calibrationEpoch: Int
) {

    init {
        require(profileId in 0..2) {
            "Unsupported profileId: $profileId"
        }

        require(sessionId in 0..0xFFFF_FFFFL) {
            "sessionId must fit uint32"
        }

        require(frameSequence in 0..0xFFFF_FFFFL) {
            "frameSequence must fit uint32"
        }

        require(sourceBlockNumber in 0..0xFFFF) {
            "sourceBlockNumber must fit uint16"
        }

        require(paletteId in 0..0xFF) {
            "paletteId must fit uint8"
        }

        require(frameType in 0..0xFF) {
            "frameType must fit uint8"
        }

        require(flags in 0..0xFF) {
            "flags must fit uint8"
        }

        require(calibrationEpoch in 0..0xFF) {
            "calibrationEpoch must fit uint8"
        }
    }
}