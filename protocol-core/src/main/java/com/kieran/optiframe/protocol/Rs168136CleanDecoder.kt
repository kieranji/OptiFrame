package com.kieran.optiframe.protocol

object Rs168136CleanDecoder {

    const val DATA_BYTES = 136
    const val CODEWORD_BYTES = 168

    private const val SHORTENING_BYTES = 87

    /**
     * Decode a shortened RS(168,136) codeword only when it contains
     * no transmission errors.
     *
     * Error/erasure correction will be implemented in the next stage.
     */
    fun decode(codeword: ByteArray): ByteArray {

        require(codeword.size == CODEWORD_BYTES) {
            "RS(168,136) codeword must be exactly " +
                    "$CODEWORD_BYTES bytes"
        }

        // Restore the 87 virtual zero bytes used by shortening.
        val fullCodeword =
            ByteArray(
                Rs255223Encoder.CODEWORD_BYTES
            )

        codeword.copyInto(
            destination = fullCodeword,
            destinationOffset = SHORTENING_BYTES
        )

        val syndromes =
            Rs255223Encoder.syndromes(
                fullCodeword
            )

        require(
            syndromes.all { it == 0 }
        ) {
            "RS codeword contains errors; " +
                    "error correction is not implemented in clean decoder"
        }

        // Shortened code is systematic:
        // first 136 transmitted bytes are data.
        return codeword.copyOfRange(
            0,
            DATA_BYTES
        )
    }
}