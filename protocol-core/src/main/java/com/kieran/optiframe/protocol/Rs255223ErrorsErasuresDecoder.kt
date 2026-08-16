package com.kieran.optiframe.protocol

object Rs255223ErrorsErasuresDecoder {

    private const val EC_BYTES = 32
    private const val CODEWORD_BYTES = 255

    fun decode(
        received: ByteArray,
        erasurePositions: IntArray = intArrayOf()
    ): ByteArray {

        require(received.size == CODEWORD_BYTES) {
            "RS(255,223) requires exactly $CODEWORD_BYTES bytes"
        }

        require(erasurePositions.size <= EC_BYTES) {
            "Too many erasures: ${erasurePositions.size}"
        }

        require(
            erasurePositions.distinct().size ==
                    erasurePositions.size
        ) {
            "Erasure positions must be unique"
        }

        erasurePositions.forEach { position ->
            require(position in received.indices) {
                "Invalid erasure position: $position"
            }
        }

        val syndromes =
            Rs255223Encoder.syndromes(received)

        if (syndromes.all { it == 0 }) {
            return received.copyOf()
        }

        /*
         * Remove the known erasure contribution from the
         * syndrome sequence.
         *
         * The resulting Forney syndrome sequence contains
         * information about unknown errors only.
         */
        val forneySyndromes =
            calculateForneySyndromes(
                syndromes = syndromes,
                erasurePositions = erasurePositions
            )

        /*
         * Berlekamp-Massey finds the locator polynomial
         * for the remaining unknown errors.
         *
         * Coefficients are stored low-degree first:
         *
         * locator[0]
         * + locator[1] x
         * + locator[2] x²
         * + ...
         */
        val unknownErrorLocator =
            berlekampMassey(
                forneySyndromes
            )

        val unknownErrorCount =
            unknownErrorLocator.size - 1

        require(
            2 * unknownErrorCount +
                    erasurePositions.size <= EC_BYTES
        ) {
            "RS capacity exceeded: " +
                    "errors=$unknownErrorCount, " +
                    "erasures=${erasurePositions.size}"
        }

        val unknownErrorPositions =
            findErrorPositions(
                locator = unknownErrorLocator,
                excludedPositions = erasurePositions.toSet()
            )

        require(
            unknownErrorPositions.size ==
                    unknownErrorCount
        ) {
            "Could not locate all unknown RS errors"
        }

        val allErrataPositions =
            IntArray(
                erasurePositions.size +
                        unknownErrorPositions.size
            )

        erasurePositions.copyInto(
            destination = allErrataPositions,
            destinationOffset = 0
        )

        unknownErrorPositions.copyInto(
            destination = allErrataPositions,
            destinationOffset =
                erasurePositions.size
        )

        val magnitudes =
            solveErrorMagnitudes(
                syndromes = syndromes,
                errataPositions = allErrataPositions
            )

        val corrected =
            received.copyOf()

        for (i in allErrataPositions.indices) {

            val position =
                allErrataPositions[i]

            corrected[position] =
                (
                        corrected[position].toInt() xor
                                magnitudes[i]
                        ).toByte()
        }

        /*
         * Never trust the mathematical correction result
         * without re-validating the complete RS codeword.
         */
        val verification =
            Rs255223Encoder.syndromes(
                corrected
            )

        require(
            verification.all { it == 0 }
        ) {
            "RS errors/erasures correction failed"
        }

        return corrected
    }

    private fun calculateForneySyndromes(
        syndromes: IntArray,
        erasurePositions: IntArray
    ): IntArray {

        var result =
            syndromes.copyOf()

        for (position in erasurePositions) {

            val x =
                Gf256.exp(
                    CODEWORD_BYTES -
                            1 -
                            position
                )

            for (i in 0 until result.size - 1) {

                result[i] =
                    Gf256.multiply(
                        result[i],
                        x
                    ) xor
                            result[i + 1]
            }

            /*
             * One known erasure consumes one syndrome
             * degree of freedom.
             */
            result =
                result.copyOf(
                    result.size - 1
                )
        }

        return result
    }

    private fun berlekampMassey(
        sequence: IntArray
    ): IntArray {

        if (sequence.isEmpty()) {
            return intArrayOf(1)
        }

        val maxSize =
            sequence.size + 1

        var locator =
            IntArray(maxSize)

        var previous =
            IntArray(maxSize)

        locator[0] = 1
        previous[0] = 1

        var degree = 0
        var shift = 1
        var previousDiscrepancy = 1

        for (n in sequence.indices) {

            var discrepancy =
                sequence[n]

            for (i in 1..degree) {

                discrepancy =
                    discrepancy xor
                            Gf256.multiply(
                                locator[i],
                                sequence[n - i]
                            )
            }

            if (discrepancy == 0) {

                shift++
                continue
            }

            val oldLocator =
                locator.copyOf()

            val scale =
                Gf256.divide(
                    discrepancy,
                    previousDiscrepancy
                )

            for (
            i in 0 until
                    maxSize - shift
            ) {

                if (previous[i] == 0) {
                    continue
                }

                locator[i + shift] =
                    locator[i + shift] xor
                            Gf256.multiply(
                                scale,
                                previous[i]
                            )
            }

            if (2 * degree <= n) {

                degree =
                    n + 1 - degree

                previous =
                    oldLocator

                previousDiscrepancy =
                    discrepancy

                shift = 1
            } else {

                shift++
            }
        }

        return locator.copyOfRange(
            0,
            degree + 1
        )
    }

    private fun findErrorPositions(
        locator: IntArray,
        excludedPositions: Set<Int>
    ): IntArray {

        val expected =
            locator.size - 1

        if (expected == 0) {
            return intArrayOf()
        }

        val positions =
            mutableListOf<Int>()

        /*
         * Instead of converting roots back through logarithms,
         * simply test every possible RS codeword position.
         *
         * For position p:
         *
         * X = alpha^(254 - p)
         *
         * The locator polynomial has its root at X^-1.
         */
        for (position in 0 until CODEWORD_BYTES) {

            if (position in excludedPositions) {
                continue
            }

            val location =
                Gf256.exp(
                    CODEWORD_BYTES -
                            1 -
                            position
                )

            val root =
                Gf256.inverse(
                    location
                )

            if (
                evaluateLowDegreePolynomial(
                    locator,
                    root
                ) == 0
            ) {

                positions += position
            }
        }

        require(
            positions.size == expected
        ) {
            "Expected $expected unknown errors, " +
                    "found ${positions.size}"
        }

        return positions.toIntArray()
    }

    private fun solveErrorMagnitudes(
        syndromes: IntArray,
        errataPositions: IntArray
    ): IntArray {

        val count =
            errataPositions.size

        if (count == 0) {
            return intArrayOf()
        }

        /*
         * Syndrome equations:
         *
         * S_i =
         * sum_j magnitude_j * X_j^i
         *
         * The error/erasure locations are already known,
         * so this becomes a small Vandermonde system over
         * GF(256).
         */
        val locations =
            IntArray(count) { index ->

                Gf256.exp(
                    CODEWORD_BYTES -
                            1 -
                            errataPositions[index]
                )
            }

        val matrix =
            Array(count) {
                IntArray(count)
            }

        for (column in 0 until count) {

            var value = 1

            for (row in 0 until count) {

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
                count
            )

        return Gf256LinearSolver.solve(
            matrix,
            rhs
        )
    }

    private fun evaluateLowDegreePolynomial(
        coefficients: IntArray,
        value: Int
    ): Int {

        var result = 0

        for (i in coefficients.indices.reversed()) {

            result =
                Gf256.multiply(
                    result,
                    value
                ) xor
                        coefficients[i]
        }

        return result
    }
}