package com.kieran.optiframe.protocol

object Rs255243ErrorsErasuresDecoder {

    private const val EC_BYTES = 12
    private const val CODEWORD_BYTES = 255

    fun decode(
        received: ByteArray,
        erasurePositions: IntArray = intArrayOf()
    ): ByteArray {

        require(received.size == CODEWORD_BYTES) {
            "RS(255,243) requires exactly 255 bytes"
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
            Rs255243Encoder.syndromes(
                received
            )

        if (syndromes.all { it == 0 }) {
            return received.copyOf()
        }

        val forneySyndromes =
            calculateForneySyndromes(
                syndromes,
                erasurePositions
            )

        val unknownErrorLocator =
            berlekampMassey(
                forneySyndromes
            )

        val unknownErrorCount =
            unknownErrorLocator.size - 1

        require(
            2 * unknownErrorCount +
                    erasurePositions.size <=
                    EC_BYTES
        ) {
            "RS(255,243) capacity exceeded: " +
                    "errors=$unknownErrorCount, " +
                    "erasures=${erasurePositions.size}"
        }

        val unknownErrorPositions =
            findErrorPositions(
                locator =
                    unknownErrorLocator,
                excludedPositions =
                    erasurePositions.toSet()
            )

        require(
            unknownErrorPositions.size ==
                    unknownErrorCount
        )

        val allPositions =
            IntArray(
                erasurePositions.size +
                        unknownErrorPositions.size
            )

        erasurePositions.copyInto(
            allPositions,
            destinationOffset = 0
        )

        unknownErrorPositions.copyInto(
            allPositions,
            destinationOffset =
                erasurePositions.size
        )

        val magnitudes =
            solveErrorMagnitudes(
                syndromes,
                allPositions
            )

        val corrected =
            received.copyOf()

        for (i in allPositions.indices) {

            val position =
                allPositions[i]

            corrected[position] =
                (
                        corrected[position].toInt() xor
                                magnitudes[i]
                        ).toByte()
        }

        val verification =
            Rs255243Encoder.syndromes(
                corrected
            )

        require(
            verification.all { it == 0 }
        ) {
            "RS(255,243) correction failed"
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

            for (
            i in
            0 until result.size - 1
            ) {

                result[i] =
                    Gf256.multiply(
                        result[i],
                        x
                    ) xor
                            result[i + 1]
            }

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
            i in
            0 until maxSize - shift
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

        for (
        position in
        0 until CODEWORD_BYTES
        ) {

            if (
                position in
                excludedPositions
            ) {
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
            "Expected $expected errors, " +
                    "found ${positions.size}"
        }

        return positions.toIntArray()
    }

    private fun solveErrorMagnitudes(
        syndromes: IntArray,
        positions: IntArray
    ): IntArray {

        val count =
            positions.size

        if (count == 0) {
            return intArrayOf()
        }

        val locations =
            IntArray(count) { index ->

                Gf256.exp(
                    CODEWORD_BYTES -
                            1 -
                            positions[index]
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

        for (
        i in
        coefficients.indices.reversed()
        ) {

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