package com.kieran.optiframe.protocol

data class Of8ObservedMatrixDecodeResult(
    val interleaved336: ByteArray,
    val erasurePositions: IntArray,
    val byteConfidence: DoubleArray
)

object Of8ObservedMatrixDecoder {

    fun decode(
        matrix: Of8LogicalMatrix,
        spatialCellConfidence: DoubleArray,
        frameSequence: Long,
        slotIndex: Int,
        erasureThreshold: Double
    ): Of8ObservedMatrixDecodeResult {

        require(
            spatialCellConfidence.size ==
                    ProtocolConstants.OF8_MACROBLOCK_CELLS
        ) {
            "Expected " +
                    "${ProtocolConstants.OF8_MACROBLOCK_CELLS} " +
                    "cell confidence values"
        }

        /*
         * Symbols and confidence MUST undergo the same
         * inverse spatial permutation.
         */

        val interleaved336 =
            Of8MatrixCodec.decode(
                matrix = matrix,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        val canonicalConfidence =
            Of8SpatialPermutation
                .inversePermuteConfidence(
                    permuted = spatialCellConfidence,
                    frameSequence = frameSequence,
                    slotIndex = slotIndex
                )

        val byteConfidence =
            Of8ConfidenceMapper
                .cellConfidenceToByteConfidence(
                    canonicalConfidence
                )

        val erasures =
            Of8ConfidenceMapper
                .erasurePositions(
                    byteConfidence = byteConfidence,
                    threshold = erasureThreshold
                )

        return Of8ObservedMatrixDecodeResult(
            interleaved336 = interleaved336,
            erasurePositions = erasures,
            byteConfidence = byteConfidence
        )
    }
}