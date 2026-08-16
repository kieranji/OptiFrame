package com.kieran.optiframe.protocol

object Of8MatrixCodec {

    fun encode(
        interleaved336: ByteArray,
        frameSequence: Long,
        slotIndex: Int
    ): Of8LogicalMatrix {

        val symbols =
            Of8SymbolCodec.bytesToSymbols(
                interleaved336
            )

        val permuted =
            Of8SpatialPermutation.permute(
                symbols = symbols,
                frameSequence = frameSequence,
                slotIndex = slotIndex
            )

        return Of8LogicalMatrix(
            cells = permuted
        )
    }

    fun decode(
        matrix: Of8LogicalMatrix,
        frameSequence: Long,
        slotIndex: Int
    ): ByteArray {

        val symbols =
            Of8SpatialPermutation
                .inversePermute(
                    permuted = matrix.cells,
                    frameSequence = frameSequence,
                    slotIndex = slotIndex
                )

        return Of8SymbolCodec
            .symbolsToBytes(symbols)
    }
}