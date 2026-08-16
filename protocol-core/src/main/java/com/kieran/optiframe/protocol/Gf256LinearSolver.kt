package com.kieran.optiframe.protocol

internal object Gf256LinearSolver {

    fun solve(
        matrixInput: Array<IntArray>,
        rhsInput: IntArray
    ): IntArray {

        val n = rhsInput.size

        require(matrixInput.size == n) {
            "Matrix must have $n rows"
        }

        require(
            matrixInput.all { it.size == n }
        ) {
            "Matrix must be square"
        }

        val matrix =
            Array(n) { row ->
                matrixInput[row].copyOf()
            }

        val rhs =
            rhsInput.copyOf()

        for (column in 0 until n) {

            var pivotRow = column

            while (
                pivotRow < n &&
                matrix[pivotRow][column] == 0
            ) {
                pivotRow++
            }

            require(pivotRow < n) {
                "Singular GF(256) matrix"
            }

            if (pivotRow != column) {

                val tempRow =
                    matrix[column]

                matrix[column] =
                    matrix[pivotRow]

                matrix[pivotRow] =
                    tempRow

                val tempValue =
                    rhs[column]

                rhs[column] =
                    rhs[pivotRow]

                rhs[pivotRow] =
                    tempValue
            }

            val pivot =
                matrix[column][column]

            val pivotInverse =
                Gf256.inverse(pivot)

            // Normalize pivot row.
            for (j in column until n) {
                matrix[column][j] =
                    Gf256.multiply(
                        matrix[column][j],
                        pivotInverse
                    )
            }

            rhs[column] =
                Gf256.multiply(
                    rhs[column],
                    pivotInverse
                )

            // Eliminate this column in every other row.
            for (row in 0 until n) {

                if (row == column) {
                    continue
                }

                val factor =
                    matrix[row][column]

                if (factor == 0) {
                    continue
                }

                for (j in column until n) {

                    matrix[row][j] =
                        matrix[row][j] xor
                                Gf256.multiply(
                                    factor,
                                    matrix[column][j]
                                )
                }

                rhs[row] =
                    rhs[row] xor
                            Gf256.multiply(
                                factor,
                                rhs[column]
                            )
            }
        }

        return rhs
    }
}