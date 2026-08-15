package com.kieran.optiframe.protocol

object Rs255223Encoder {

    const val DATA_BYTES = 223
    const val PARITY_BYTES = 32
    const val CODEWORD_BYTES = 255

    /*
     * Generator polynomial:
     *
     * g(x) = Π(i=0..31) (x - α^i)
     *
     * In GF(2^8), subtraction and addition are both XOR.
     *
     * Coefficients are stored highest-degree first.
     */
    private val generator: IntArray by lazy {
        buildGeneratorPolynomial()
    }

    fun encode(data: ByteArray): ByteArray {
        require(data.size == DATA_BYTES) {
            "RS(255,223) requires exactly $DATA_BYTES data bytes, " +
                    "but got ${data.size}"
        }

        val work = IntArray(CODEWORD_BYTES)

        // Message is placed in the high-order coefficients.
        for (i in data.indices) {
            work[i] = data[i].toInt() and 0xFF
        }

        // Polynomial long division by g(x).
        for (i in 0 until DATA_BYTES) {
            val coefficient = work[i]

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

        val result = ByteArray(CODEWORD_BYTES)

        // Systematic RS:
        // original message first...
        data.copyInto(result, destinationOffset = 0)

        // ...followed by 32 parity bytes.
        for (i in 0 until PARITY_BYTES) {
            result[DATA_BYTES + i] =
                work[DATA_BYTES + i].toByte()
        }

        return result
    }

    fun parity(data: ByteArray): ByteArray {
        return encode(data)
            .copyOfRange(
                DATA_BYTES,
                CODEWORD_BYTES
            )
    }

    /**
     * Used by tests now and by the decoder later.
     *
     * A valid RS(255,223) codeword must evaluate to zero
     * at α^0 ... α^31.
     */
    fun syndromes(codeword: ByteArray): IntArray {
        require(codeword.size == CODEWORD_BYTES) {
            "Expected $CODEWORD_BYTES bytes"
        }

        return IntArray(PARITY_BYTES) { rootPower ->

            val root = Gf256.exp(rootPower)
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

        /*
         * Building with coefficients in ascending degree order
         * makes multiplication by (x + root) straightforward.
         */
        var polynomial =
            intArrayOf(1)

        for (rootPower in 0 until PARITY_BYTES) {

            val root =
                Gf256.exp(rootPower)

            val next =
                IntArray(polynomial.size + 1)

            for (i in polynomial.indices) {

                // Existing coefficient × root
                next[i] =
                    next[i] xor
                            Gf256.multiply(
                                polynomial[i],
                                root
                            )

                // Existing coefficient × x
                next[i + 1] =
                    next[i + 1] xor
                            polynomial[i]
            }

            polynomial = next
        }

        // Polynomial division code expects
        // highest-degree coefficient first.
        return polynomial.reversedArray()
    }
}