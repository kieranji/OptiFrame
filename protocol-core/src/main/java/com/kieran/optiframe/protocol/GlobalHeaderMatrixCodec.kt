package com.kieran.optiframe.protocol

object GlobalHeaderMatrixCodec {

    private val FIXED_TRAILER =
        intArrayOf(
            1, 0, 1, 0,
            1, 0, 1, 0
        )

    fun encode(
        protected31: ByteArray
    ): GlobalHeaderLogicalMatrix {

        require(
            protected31.size ==
                    ProtocolConstants.GLOBAL_HEADER_RS_BYTES
        ) {
            "Protected Global Header must contain exactly 31 bytes"
        }

        val cells =
            IntArray(
                ProtocolConstants.GLOBAL_HEADER_MATRIX_CELLS
            )

        var bitIndex = 0

        for (byte in protected31) {

            val value =
                byte.toInt() and 0xFF

            for (shift in 7 downTo 0) {

                cells[bitIndex++] =
                    (value ushr shift) and 0x01
            }
        }

        check(
            bitIndex ==
                    ProtocolConstants.GLOBAL_HEADER_RS_BITS
        )

        FIXED_TRAILER.copyInto(
            destination = cells,
            destinationOffset = bitIndex
        )

        return GlobalHeaderLogicalMatrix(
            cells
        )
    }

    fun decode(
        matrix: GlobalHeaderLogicalMatrix
    ): ByteArray {

        validateFixedTrailer(matrix)

        val result =
            ByteArray(
                ProtocolConstants.GLOBAL_HEADER_RS_BYTES
            )

        var bitIndex = 0

        for (byteIndex in result.indices) {

            var value = 0

            repeat(8) {

                value =
                    (value shl 1) or
                            matrix.cells[bitIndex]

                bitIndex++
            }

            result[byteIndex] =
                value.toByte()
        }

        check(
            bitIndex ==
                    ProtocolConstants.GLOBAL_HEADER_RS_BITS
        )

        return result
    }

    fun validateFixedTrailer(
        matrix: GlobalHeaderLogicalMatrix
    ) {

        val start =
            ProtocolConstants.GLOBAL_HEADER_RS_BITS

        for (i in FIXED_TRAILER.indices) {

            require(
                matrix.cells[start + i] ==
                        FIXED_TRAILER[i]
            ) {
                "Invalid Global Header fixed trailer"
            }
        }
    }
}