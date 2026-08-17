package com.kieran.optiframe.protocol

object OptiFrameGlobalHeaderMatrixCodec {

    fun encode(
        header: OptiFrameGlobalHeader
    ): GlobalHeaderLogicalMatrix {

        val protected =
            OptiFrameProtectedGlobalHeaderCodec
                .encode(header)

        return GlobalHeaderMatrixCodec.encode(
            protected
        )
    }

    fun decode(
        matrix: GlobalHeaderLogicalMatrix,
        erasurePositions: IntArray =
            intArrayOf()
    ): OptiFrameGlobalHeader {

        val protected =
            GlobalHeaderMatrixCodec.decode(
                matrix
            )

        return OptiFrameProtectedGlobalHeaderCodec
            .decode(
                protected31 = protected,
                erasurePositions =
                    erasurePositions
            )
    }
}