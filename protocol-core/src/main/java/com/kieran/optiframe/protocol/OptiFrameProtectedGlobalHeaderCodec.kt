package com.kieran.optiframe.protocol

object OptiFrameProtectedGlobalHeaderCodec {

    fun encode(
        header: OptiFrameGlobalHeader
    ): ByteArray {

        val data19 =
            OptiFrameGlobalHeaderCodec.encode(
                header
            )

        return Rs31219Encoder.encode(
            data19
        )
    }

    fun cleanDecode(
        protected31: ByteArray
    ): OptiFrameGlobalHeader {

        val data19 =
            Rs31219Encoder.cleanDecode(
                protected31
            )

        return OptiFrameGlobalHeaderCodec.decode(
            data19
        )
    }

    fun decode(
        protected31: ByteArray,
        erasurePositions: IntArray =
            intArrayOf()
    ): OptiFrameGlobalHeader {

        val data19 =
            Rs3119ErrorsErasuresDecoder.decode(
                received = protected31,
                erasurePositions =
                    erasurePositions
            )

        /*
         * RS 成功并不够。
         *
         * 这里还必须经过 header CRC-8，
         * 用来防止 RS 超能力后的误纠。
         */
        return OptiFrameGlobalHeaderCodec.decode(
            data19
        )
    }
}