package com.kieran.optiframe.protocol

object Crc8Atm {

    private const val POLYNOMIAL =
        ProtocolConstants.CRC8_ATM_POLYNOMIAL

    fun compute(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset
    ): Int {

        require(offset >= 0)
        require(length >= 0)
        require(offset + length <= data.size)

        var crc = 0x00

        for (i in offset until offset + length) {

            crc =
                crc xor
                        (data[i].toInt() and 0xFF)

            repeat(8) {

                crc =
                    if ((crc and 0x80) != 0) {
                        ((crc shl 1) xor POLYNOMIAL) and 0xFF
                    } else {
                        (crc shl 1) and 0xFF
                    }
            }
        }

        return crc
    }
}