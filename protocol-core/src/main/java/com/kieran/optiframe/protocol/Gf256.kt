package com.kieran.optiframe.protocol

object Gf256 {

    private const val FIELD_SIZE = 256
    private const val ORDER = 255
    private const val PRIMITIVE_POLYNOMIAL =
        ProtocolConstants.GF256_PRIMITIVE_POLYNOMIAL

    private val expTable = IntArray(ORDER * 2)
    private val logTable = IntArray(FIELD_SIZE)

    init {
        var x = 1

        for (i in 0 until ORDER) {
            expTable[i] = x
            logTable[x] = i

            x = x shl 1

            if ((x and 0x100) != 0) {
                x = x xor PRIMITIVE_POLYNOMIAL
            }
        }

        for (i in ORDER until expTable.size) {
            expTable[i] =
                expTable[i - ORDER]
        }
    }

    fun add(a: Int, b: Int): Int {
        checkElement(a)
        checkElement(b)

        return a xor b
    }

    fun subtract(a: Int, b: Int): Int {
        // In characteristic 2:
        // subtraction == addition == XOR.
        return add(a, b)
    }

    fun multiply(a: Int, b: Int): Int {
        checkElement(a)
        checkElement(b)

        if (a == 0 || b == 0) {
            return 0
        }

        return expTable[
            logTable[a] + logTable[b]
        ]
    }

    fun inverse(a: Int): Int {
        checkElement(a)

        require(a != 0) {
            "0 has no multiplicative inverse in GF(256)"
        }

        return expTable[
            ORDER - logTable[a]
        ]
    }

    fun divide(a: Int, b: Int): Int {
        checkElement(a)
        checkElement(b)

        require(b != 0) {
            "Division by zero in GF(256)"
        }

        if (a == 0) {
            return 0
        }

        var exponent =
            logTable[a] - logTable[b]

        if (exponent < 0) {
            exponent += ORDER
        }

        return expTable[exponent]
    }

    fun exp(power: Int): Int {
        var normalized = power % ORDER

        if (normalized < 0) {
            normalized += ORDER
        }

        return expTable[normalized]
    }

    fun log(value: Int): Int {
        checkElement(value)

        require(value != 0) {
            "log(0) is undefined in GF(256)"
        }

        return logTable[value]
    }

    private fun checkElement(value: Int) {
        require(value in 0..0xFF) {
            "GF(256) element out of range: $value"
        }
    }
}