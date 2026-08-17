package com.kieran.optiframe.protocol

object Of8ConfidenceMapper {

    const val CELL_COUNT =
        ProtocolConstants.OF8_MACROBLOCK_CELLS

    const val BYTE_COUNT =
        ProtocolConstants.OF8_RS_INTERLEAVED_BYTES

    /**
     * Convert canonical (already inverse-permuted) OF8 cell
     * confidences into confidence values for the 336 reconstructed bytes.
     *
     * Each OF8 cell contributes 3 consecutive bits.
     * Each output byte contains 8 consecutive bits.
     *
     * A byte's confidence is the minimum confidence of every
     * cell contributing at least one bit to that byte.
     */
    fun cellConfidenceToByteConfidence(
        cellConfidence: DoubleArray
    ): DoubleArray {

        require(cellConfidence.size == CELL_COUNT) {
            "Expected $CELL_COUNT OF8 cell confidence values, " +
                    "but got ${cellConfidence.size}"
        }

        require(
            cellConfidence.all {
                it.isFinite() && it in 0.0..1.0
            }
        ) {
            "Every confidence value must be finite and in 0.0..1.0"
        }

        val byteConfidence =
            DoubleArray(BYTE_COUNT) {
                1.0
            }

        /*
         * There are exactly:
         *
         * 896 cells × 3 bits = 2688 bits
         * 336 bytes × 8 bits = 2688 bits
         */
        val totalBits =
            CELL_COUNT * 3

        check(totalBits == BYTE_COUNT * 8)

        for (bitIndex in 0 until totalBits) {

            val cellIndex =
                bitIndex / 3

            val byteIndex =
                bitIndex / 8

            byteConfidence[byteIndex] =
                minOf(
                    byteConfidence[byteIndex],
                    cellConfidence[cellIndex]
                )
        }

        return byteConfidence
    }

    fun erasurePositions(
        byteConfidence: DoubleArray,
        threshold: Double
    ): IntArray {

        require(byteConfidence.size == BYTE_COUNT) {
            "Expected $BYTE_COUNT byte confidence values"
        }

        require(
            threshold.isFinite() &&
                    threshold in 0.0..1.0
        ) {
            "Threshold must be in 0.0..1.0"
        }

        return byteConfidence
            .indices
            .filter { index ->
                byteConfidence[index] < threshold
            }
            .toIntArray()
    }
}