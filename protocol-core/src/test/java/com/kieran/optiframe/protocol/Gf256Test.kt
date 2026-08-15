package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Gf256Test {

    @Test
    fun additionIsXor() {
        assertEquals(
            0xFF,
            Gf256.add(0xAA, 0x55)
        )

        assertEquals(
            0,
            Gf256.add(0x5A, 0x5A)
        )
    }

    @Test
    fun multiplicationMatchesKnownVectors() {
        assertEquals(
            0x8F,
            Gf256.multiply(0x53, 0xCA)
        )

        assertEquals(
            0x1D,
            Gf256.multiply(0x02, 0x80)
        )

        assertEquals(
            0x0F,
            Gf256.multiply(0x12, 0x34)
        )
    }

    @Test
    fun inverseProducesMultiplicativeIdentity() {
        val values =
            intArrayOf(
                0x01,
                0x02,
                0x03,
                0x53,
                0xCA,
                0xFF
            )

        for (value in values) {
            assertEquals(
                1,
                Gf256.multiply(
                    value,
                    Gf256.inverse(value)
                )
            )
        }
    }

    @Test
    fun exponentTableStartsCorrectly() {
        val expected =
            intArrayOf(
                0x01,
                0x02,
                0x04,
                0x08,
                0x10,
                0x20,
                0x40,
                0x80,
                0x1D,
                0x3A
            )

        expected.forEachIndexed { index, value ->
            assertEquals(
                value,
                Gf256.exp(index)
            )
        }
    }

    @Test
    fun inverseOfZeroIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            Gf256.inverse(0)
        }
    }
}