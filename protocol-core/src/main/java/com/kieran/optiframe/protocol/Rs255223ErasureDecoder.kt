package com.kieran.optiframe.protocol

object Rs255223ErasureDecoder {

    private const val MAX_ERASURES = 32

    fun decode(
        received: ByteArray,
        erasurePositions: IntArray
    ): ByteArray {

        require(
            received.size ==
                    Rs255223Encoder.CODEWORD_BYTES
        ) {
            "RS(255,223) requires exactly 255 bytes"
        }

        require(
            erasurePositions.size <= MAX_ERASURES
        ) {
            "RS(255,223) can handle at most " +
                    "$MAX_ERASURES erasures when there are no unknown errors"
        }

        require(
            erasurePositions.distinct().size ==
                    erasurePositions.size
        ) {
            "Erasure positions must be unique"
        }

        erasurePositions.forEach { position ->

            require(
                position in received.indices
            ) {
                "Invalid erasure position: $position"
            }
        }

        val corrected =
            received.copyOf()

        var syndromes =
            Rs255223Encoder.syndromes(
                corrected
            )

        // Already valid.
        if (
            syndromes.all { it == 0 }
        ) {
            return corrected
        }

        require(
            erasurePositions.isNotEmpty()
        ) {
            "Codeword contains errors but no erasures were provided"
        }

        val erasureCount =
            erasurePositions.size

        /*
         * Error at codeword position p contributes:
         *
         * magnitude × X^i
         *
         * to syndrome S_i,
         *
         * where:
         *
         * X = alpha^(254 - p)
         */
        val locations =
            IntArray(erasureCount) { index ->

                val position =
                    erasurePositions[index]

                Gf256.exp(
                    Rs255223Encoder.CODEWORD_BYTES -
                            1 -
                            position
                )
            }

        /*
         * Construct a Vandermonde system:
         *
         * S_i = sum_j magnitude_j * X_j^i
         *
         * for i = 0 ... erasureCount - 1
         */
        val matrix =
            Array(erasureCount) {
                IntArray(erasureCount)
            }

        for (column in 0 until erasureCount) {

            var value = 1

            for (row in 0 until erasureCount) {

                matrix[row][column] =
                    value

                value =
                    Gf256.multiply(
                        value,
                        locations[column]
                    )
            }
        }

        val rhs =
            syndromes.copyOfRange(
                0,
                erasureCount
            )

        val magnitudes =
            Gf256LinearSolver.solve(
                matrix,
                rhs
            )

        for (i in 0 until erasureCount) {

            val position =
                erasurePositions[i]

            corrected[position] =
                (
                        corrected[position].toInt() xor
                                magnitudes[i]
                        ).toByte()
        }

        // Never accept an RS result without verification.
        syndromes =
            Rs255223Encoder.syndromes(
                corrected
            )

        require(
            syndromes.all { it == 0 }
        ) {
            "Erasure correction failed; " +
                    "unknown errors may also be present"
        }

        return corrected
    }
}