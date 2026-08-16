package com.kieran.optiframe.protocol

object Rs255223Decoder {

    private const val EC_BYTES = 32

    fun decode(
        received: ByteArray
    ): ByteArray {

        require(
            received.size ==
                    Rs255223Encoder.CODEWORD_BYTES
        ) {
            "RS(255,223) requires exactly 255 bytes"
        }

        val corrected =
            received.copyOf()

        val receivedInts =
            IntArray(corrected.size) {
                corrected[it].toInt() and 0xFF
            }

        val receivedPolynomial =
            Gf256Polynomial(
                receivedInts
            )

        val syndromeCoefficients =
            IntArray(EC_BYTES)

        var hasError =
            false

        for (i in 0 until EC_BYTES) {

            val evaluation =
                receivedPolynomial.evaluateAt(
                    Gf256.exp(i)
                )

            syndromeCoefficients[
                EC_BYTES - 1 - i
            ] = evaluation

            if (evaluation != 0) {
                hasError = true
            }
        }

        if (!hasError) {
            return corrected
        }

        val syndrome =
            Gf256Polynomial(
                syndromeCoefficients
            )

        val monomial =
            buildMonomial(
                EC_BYTES,
                1
            )

        val (sigma, omega) =
            runEuclideanAlgorithm(
                monomial,
                syndrome,
                EC_BYTES
            )

        val errorLocations =
            findErrorLocations(
                sigma
            )

        val errorMagnitudes =
            findErrorMagnitudes(
                omega,
                errorLocations
            )

        for (i in errorLocations.indices) {

            val position =
                corrected.size -
                        1 -
                        Gf256.log(
                            errorLocations[i]
                        )

            require(position >= 0) {
                "Invalid Reed-Solomon error location"
            }

            corrected[position] =
                (
                        corrected[position].toInt() xor
                                errorMagnitudes[i]
                        ).toByte()
        }

        // Do not trust the result until all syndromes are zero.
        val verification =
            Rs255223Encoder.syndromes(
                corrected
            )

        require(
            verification.all { it == 0 }
        ) {
            "Reed-Solomon decoding failed"
        }

        return corrected
    }

    private fun runEuclideanAlgorithm(
        aInput: Gf256Polynomial,
        bInput: Gf256Polynomial,
        rValue: Int
    ): Pair<Gf256Polynomial, Gf256Polynomial> {

        var a =
            aInput

        var b =
            bInput

        if (a.degree < b.degree) {
            val temp = a
            a = b
            b = temp
        }

        var rLast =
            a

        var r =
            b

        var tLast =
            Gf256Polynomial(
                intArrayOf(0)
            )

        var t =
            Gf256Polynomial(
                intArrayOf(1)
            )

        while (
            r.degree >= rValue / 2
        ) {

            val rLastLast =
                rLast

            val tLastLast =
                tLast

            rLast =
                r

            tLast =
                t

            require(!rLast.isZero) {
                "RS Euclidean algorithm reached zero polynomial"
            }

            r =
                rLastLast

            var quotient =
                Gf256Polynomial(
                    intArrayOf(0)
                )

            val denominatorLeadingTerm =
                rLast.coefficient(
                    rLast.degree
                )

            val inverseDenominator =
                Gf256.inverse(
                    denominatorLeadingTerm
                )

            while (
                r.degree >= rLast.degree &&
                !r.isZero
            ) {

                val degreeDifference =
                    r.degree -
                            rLast.degree

                val scale =
                    Gf256.multiply(
                        r.coefficient(
                            r.degree
                        ),
                        inverseDenominator
                    )

                quotient =
                    quotient.addOrSubtract(
                        buildMonomial(
                            degreeDifference,
                            scale
                        )
                    )

                r =
                    r.addOrSubtract(
                        rLast.multiplyByMonomial(
                            degreeDifference,
                            scale
                        )
                    )
            }

            t =
                quotient.multiply(
                    tLast
                ).addOrSubtract(
                    tLastLast
                )
        }

        val sigmaAtZero =
            t.coefficient(0)

        require(sigmaAtZero != 0) {
            "Invalid Reed-Solomon error locator"
        }

        val inverse =
            Gf256.inverse(
                sigmaAtZero
            )

        val sigma =
            t.multiplyByScalar(
                inverse
            )

        val omega =
            r.multiplyByScalar(
                inverse
            )

        return sigma to omega
    }

    private fun findErrorLocations(
        errorLocator: Gf256Polynomial
    ): IntArray {

        val errorCount =
            errorLocator.degree

        if (errorCount == 1) {
            return intArrayOf(
                errorLocator.coefficient(1)
            )
        }

        val result =
            IntArray(errorCount)

        var found =
            0

        for (i in 1 until 256) {

            if (
                errorLocator.evaluateAt(i) == 0
            ) {

                result[found] =
                    Gf256.inverse(i)

                found++

                if (found == errorCount) {
                    break
                }
            }
        }

        require(found == errorCount) {
            "Could not locate all Reed-Solomon errors"
        }

        return result
    }

    private fun findErrorMagnitudes(
        errorEvaluator: Gf256Polynomial,
        errorLocations: IntArray
    ): IntArray {

        val result =
            IntArray(
                errorLocations.size
            )

        for (i in errorLocations.indices) {

            val xiInverse =
                Gf256.inverse(
                    errorLocations[i]
                )

            var denominator =
                1

            for (j in errorLocations.indices) {

                if (i == j) {
                    continue
                }

                val term =
                    Gf256.multiply(
                        errorLocations[j],
                        xiInverse
                    )

                // In GF(2^8), subtraction == XOR.
                denominator =
                    Gf256.multiply(
                        denominator,
                        term xor 1
                    )
            }

            result[i] =
                Gf256.multiply(
                    errorEvaluator.evaluateAt(
                        xiInverse
                    ),
                    Gf256.inverse(
                        denominator
                    )
                )
        }

        return result
    }

    private fun buildMonomial(
        degree: Int,
        coefficient: Int
    ): Gf256Polynomial {

        require(degree >= 0)

        if (coefficient == 0) {
            return Gf256Polynomial(
                intArrayOf(0)
            )
        }

        val coefficients =
            IntArray(
                degree + 1
            )

        coefficients[0] =
            coefficient

        return Gf256Polynomial(
            coefficients
        )
    }
}