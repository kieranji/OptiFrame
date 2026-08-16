package com.kieran.optiframe.protocol

object Of8SpatialPermutation {

    const val N = ProtocolConstants.OF8_MACROBLOCK_CELLS

    private val A_TABLE =
        intArrayOf(
            131, 173, 229, 277,
            331, 373, 419, 461,
            523, 571, 613, 659,
            733, 779, 821, 877
        )

    data class Parameters(
        val a: Int,
        val b: Int,
        val aInverse: Int
    )

    fun deriveParameters(
        frameSequence: Long,
        slotIndex: Int
    ): Parameters {

        require(frameSequence in 0..0xFFFF_FFFFL) {
            "frameSequence must fit uint32"
        }

        require(slotIndex in 0..0xFF) {
            "slotIndex must fit uint8"
        }

        val input = ByteArray(7)

        BinaryIo.writeUInt32BE(
            frameSequence
        ).copyInto(input, 0)

        input[4] =
            slotIndex.toByte()

        // Domain separator: ASCII "PI"
        input[5] = 0x50
        input[6] = 0x49

        val digest =
            Sha256.digest(input)

        val aIndex =
            (digest[0].toInt() and 0xFF) %
                    A_TABLE.size

        val a =
            A_TABLE[aIndex]

        val bRaw =
            ((digest[1].toLong() and 0xFF) shl 24) or
                    ((digest[2].toLong() and 0xFF) shl 16) or
                    ((digest[3].toLong() and 0xFF) shl 8) or
                    (digest[4].toLong() and 0xFF)

        val b =
            (bRaw % N).toInt()

        val aInverse =
            modularInverse(
                a,
                N
            )

        return Parameters(
            a = a,
            b = b,
            aInverse = aInverse
        )
    }

    fun permute(
        symbols: IntArray,
        frameSequence: Long,
        slotIndex: Int
    ): IntArray {

        require(symbols.size == N) {
            "Expected $N OF8 symbols"
        }

        val params =
            deriveParameters(
                frameSequence,
                slotIndex
            )

        val result =
            IntArray(N)

        for (i in 0 until N) {
            val j =
                (
                        params.a.toLong() * i +
                                params.b
                        ).mod(N.toLong())
                    .toInt()

            result[j] = symbols[i]
        }

        return result
    }

    fun inversePermute(
        permuted: IntArray,
        frameSequence: Long,
        slotIndex: Int
    ): IntArray {

        require(permuted.size == N) {
            "Expected $N OF8 symbols"
        }

        val params =
            deriveParameters(
                frameSequence,
                slotIndex
            )

        val result =
            IntArray(N)

        for (j in 0 until N) {

            val delta =
                floorMod(
                    j - params.b,
                    N
                )

            val i =
                (
                        params.aInverse.toLong() *
                                delta
                        ).mod(N.toLong())
                    .toInt()

            result[i] =
                permuted[j]
        }

        return result
    }

    private fun modularInverse(
        value: Int,
        modulus: Int
    ): Int {

        var t = 0
        var newT = 1

        var r = modulus
        var newR = value

        while (newR != 0) {

            val quotient =
                r / newR

            val tempT =
                t - quotient * newT

            t = newT
            newT = tempT

            val tempR =
                r - quotient * newR

            r = newR
            newR = tempR
        }

        require(r == 1) {
            "$value has no inverse modulo $modulus"
        }

        return floorMod(
            t,
            modulus
        )
    }

    private fun floorMod(
        value: Int,
        modulus: Int
    ): Int {
        val result =
            value % modulus

        return if (result < 0) {
            result + modulus
        } else {
            result
        }
    }
}