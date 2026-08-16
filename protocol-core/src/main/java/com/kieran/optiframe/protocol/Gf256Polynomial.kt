package com.kieran.optiframe.protocol

internal class Gf256Polynomial(
    coefficientsInput: IntArray
) {

    val coefficients: IntArray

    init {
        require(coefficientsInput.isNotEmpty())

        var firstNonZero = 0

        while (
            firstNonZero < coefficientsInput.size - 1 &&
            coefficientsInput[firstNonZero] == 0
        ) {
            firstNonZero++
        }

        coefficients =
            coefficientsInput.copyOfRange(
                firstNonZero,
                coefficientsInput.size
            )
    }

    val degree: Int
        get() = coefficients.size - 1

    val isZero: Boolean
        get() = coefficients[0] == 0

    fun coefficient(
        degree: Int
    ): Int {
        return coefficients[
            coefficients.size - 1 - degree
        ]
    }

    fun evaluateAt(
        value: Int
    ): Int {

        if (value == 0) {
            return coefficient(0)
        }

        if (value == 1) {
            var result = 0

            for (coefficient in coefficients) {
                result =
                    result xor coefficient
            }

            return result
        }

        var result =
            coefficients[0]

        for (i in 1 until coefficients.size) {
            result =
                Gf256.multiply(
                    value,
                    result
                ) xor coefficients[i]
        }

        return result
    }

    fun addOrSubtract(
        other: Gf256Polynomial
    ): Gf256Polynomial {

        if (isZero) {
            return other
        }

        if (other.isZero) {
            return this
        }

        var smaller =
            coefficients

        var larger =
            other.coefficients

        if (smaller.size > larger.size) {
            val temp = smaller
            smaller = larger
            larger = temp
        }

        val result =
            larger.copyOf()

        val lengthDifference =
            larger.size - smaller.size

        for (i in smaller.indices) {
            result[i + lengthDifference] =
                result[i + lengthDifference] xor
                        smaller[i]
        }

        return Gf256Polynomial(
            result
        )
    }

    fun multiply(
        other: Gf256Polynomial
    ): Gf256Polynomial {

        if (isZero || other.isZero) {
            return Gf256Polynomial(
                intArrayOf(0)
            )
        }

        val product =
            IntArray(
                coefficients.size +
                        other.coefficients.size -
                        1
            )

        for (i in coefficients.indices) {
            val a =
                coefficients[i]

            for (j in other.coefficients.indices) {
                product[i + j] =
                    product[i + j] xor
                            Gf256.multiply(
                                a,
                                other.coefficients[j]
                            )
            }
        }

        return Gf256Polynomial(
            product
        )
    }

    fun multiplyByScalar(
        scalar: Int
    ): Gf256Polynomial {

        if (scalar == 0) {
            return Gf256Polynomial(
                intArrayOf(0)
            )
        }

        if (scalar == 1) {
            return this
        }

        return Gf256Polynomial(
            IntArray(
                coefficients.size
            ) { index ->
                Gf256.multiply(
                    coefficients[index],
                    scalar
                )
            }
        )
    }

    fun multiplyByMonomial(
        degree: Int,
        coefficient: Int
    ): Gf256Polynomial {

        require(degree >= 0)

        if (coefficient == 0) {
            return Gf256Polynomial(
                intArrayOf(0)
            )
        }

        val result =
            IntArray(
                coefficients.size + degree
            )

        for (i in coefficients.indices) {
            result[i] =
                Gf256.multiply(
                    coefficients[i],
                    coefficient
                )
        }

        return Gf256Polynomial(
            result
        )
    }
}