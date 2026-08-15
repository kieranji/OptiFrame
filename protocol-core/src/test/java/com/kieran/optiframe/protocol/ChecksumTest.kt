package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ChecksumTest {

    @Test
    fun crc32cStandardVector() {
        val data = "123456789".encodeToByteArray()

        val crc = Crc32c.compute(data)

        assertEquals(
            0xE3069283L,
            crc
        )
    }

    @Test
    fun crc32cZeroTo255Vector() {
        val data = ByteArray(256) {
            it.toByte()
        }

        val crc = Crc32c.compute(data)

        assertEquals(
            0x9C44184BL,
            crc
        )
    }

    @Test
    fun sha256ZeroTo255Vector() {
        val data = ByteArray(256) {
            it.toByte()
        }

        val digest = Sha256.hex(data)

        assertEquals(
            "40aff2e9d2d8922e47afd4648e696749" +
                    "7158785fbd1da870e7110266bf944880",
            digest
        )
    }
}