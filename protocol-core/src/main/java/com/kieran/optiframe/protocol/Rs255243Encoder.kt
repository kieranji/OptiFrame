package com.kieran.optiframe.protocol

object Rs255243Encoder {

    const val DATA_BYTES = 243
    const val PARITY_BYTES = 12
    const val CODEWORD_BYTES = 255

    private val generator: IntArray by lazy {
        buildGeneratorPolynomial()
    }

    fun encode(
        data: ByteArray
    ): ByteArray {

        require(data.size == DATA_BYTES) {
            "RS(255,243) requires exactly 243 data bytes"
        }

        val work =
            IntArray(CODEWORD_BYTES)

        for (i in data.indices) {
            work[i] =
                data[i].toInt() and 0xFF
        }

        for (i in 0 until DATA_BYTES) {

            val coefficient =
                work[i]

            if (coefficient == 0) {
                continue
            }

            for (j in generator.indices) {

                work[i + j] =
                    work[i + j] xor
                            Gf256.multiply(
                                coefficient,
                                generator[j]
                            )
            }
        }

        val result =
            ByteArray(CODEWORD_BYTES)

        data.copyInto(
            result,
            destinationOffset = 0
        )

        for (i in 0 until PARITY_BYTES) {

            result[DATA_BYTES + i] =
                work[DATA_BYTES + i]
                    .toByte()
        }

        return result
    }

    fun syndromes(
        codeword: ByteArray
    ): IntArray {

        require(
            codeword.size == CODEWORD_BYTES
        )

        return IntArray(PARITY_BYTES) { rootPower ->

            val root =
                Gf256.exp(rootPower)

            var syndrome = 0

            for (byte in codeword) {

                syndrome =
                    Gf256.multiply(
                        syndrome,
                        root
                    ) xor
                            (byte.toInt() and 0xFF)
            }

            syndrome
        }
    }

    private fun buildGeneratorPolynomial(): IntArray {

        var polynomial =
            intArrayOf(1)

        for (
        rootPower in
        0 until PARITY_BYTES
        ) {

            val root =
                Gf256.exp(rootPower)

            val next =
                IntArray(
                    polynomial.size + 1
                )

            for (i in polynomial.indices) {

                next[i] =
                    next[i] xor
                            Gf256.multiply(
                                polynomial[i],
                                root
                            )

                next[i + 1] =
                    next[i + 1] xor
                            polynomial[i]
            }

            polynomial = next
        }

        return polynomial.reversedArray()
    }
}