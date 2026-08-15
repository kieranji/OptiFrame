package com.kieran.optiframe.protocol

import java.util.zip.CRC32C

object Crc32c {

    fun compute(data: ByteArray): Long {
        val crc = CRC32C()
        crc.update(data, 0, data.size)
        return crc.value
    }

    fun compute(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Long {
        require(offset >= 0)
        require(length >= 0)
        require(offset + length <= data.size)

        val crc = CRC32C()
        crc.update(data, offset, length)
        return crc.value
    }
}