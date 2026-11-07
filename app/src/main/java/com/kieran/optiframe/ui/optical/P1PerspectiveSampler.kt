package com.kieran.optiframe.ui.optical

import androidx.camera.core.ImageProxy
import com.kieran.optiframe.protocol.P1PhysicalLayout
import com.kieran.optiframe.protocol.P1Rgb888GridDecoder

/** Rectifies an RGBA CameraX image into the protocol's one-pixel-per-cell RGB grid. */
object P1PerspectiveSampler {
    /** Image-space centers of the four PC-007 corner anchors, clockwise. */
    data class AnchorPoints(
        val topLeftX: Double, val topLeftY: Double,
        val topRightX: Double, val topRightY: Double,
        val bottomRightX: Double, val bottomRightY: Double,
        val bottomLeftX: Double, val bottomLeftY: Double
    ) {
        companion object {
            /** Center-crop a tightly framed P1 matrix from a fixed, head-on camera view. */
            fun centered(width: Int, height: Int): AnchorPoints {
                require(width > 0 && height > 0)
                val targetRatio = P1PhysicalLayout.GRID_WIDTH.toDouble() / P1PhysicalLayout.GRID_HEIGHT
                val imageRatio = width.toDouble() / height
                val cropWidth: Double
                val cropHeight: Double
                if (imageRatio > targetRatio) {
                    cropHeight = height.toDouble()
                    cropWidth = cropHeight * targetRatio
                } else {
                    cropWidth = width.toDouble()
                    cropHeight = cropWidth / targetRatio
                }
                val left = (width - cropWidth) / 2.0
                val top = (height - cropHeight) / 2.0
                val sx = cropWidth / P1PhysicalLayout.GRID_WIDTH
                val sy = cropHeight / P1PhysicalLayout.GRID_HEIGHT
                return AnchorPoints(
                    left + 8 * sx, top + 8 * sy,
                    left + 152 * sx, top + 8 * sy,
                    left + 152 * sx, top + 88 * sy,
                    left + 8 * sx, top + 88 * sy
                )
            }
        }
    }

    fun decode(image: ImageProxy, anchors: AnchorPoints): P1Rgb888GridDecoder.DecodedGrid =
        P1Rgb888GridDecoder.decode(sample(image, anchors))

    /**
     * The points are the centers of the PC-007 6×6 corner anchors, clockwise.
     * [AnchorPoints.centered] is a fixed-stand bootstrap assumption; production
     * tracking should update these points as the camera or display moves.
     */
    fun sample(image: ImageProxy, anchors: AnchorPoints): ByteArray {
        require(image.planes.size == 1) {
            "CameraX analysis must provide one RGBA plane"
        }
        val plane = image.planes.single()
        val buffer = plane.buffer.duplicate()
        val pixels = ByteArray(P1PhysicalLayout.GRID_WIDTH * P1PhysicalLayout.GRID_HEIGHT * 3)
        val h = homography(anchors)
        var destination = 0
        for (row in 0 until P1PhysicalLayout.GRID_HEIGHT) {
            for (column in 0 until P1PhysicalLayout.GRID_WIDTH) {
                val u = column + 0.5
                val v = row + 0.5
                val denominator = h[6] * u + h[7] * v + 1.0
                require(kotlin.math.abs(denominator) > 1e-9) { "Invalid projective transform" }
                val x = (h[0] * u + h[1] * v + h[2]) / denominator
                val y = (h[3] * u + h[4] * v + h[5]) / denominator
                require(x >= 0.0 && x < image.width && y >= 0.0 && y < image.height) {
                    "Calibration maps P1 cell ($column,$row) outside the camera image"
                }
                val ix = x.toInt()
                val iy = y.toInt()
                var red = 0
                var green = 0
                var blue = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val sx = (ix + dx).coerceIn(0, image.width - 1)
                    val sy = (iy + dy).coerceIn(0, image.height - 1)
                    val offset = sy * plane.rowStride + sx * plane.pixelStride
                    require(offset >= 0 && offset + 2 < buffer.limit()) { "RGBA plane layout exceeds its buffer" }
                    red += buffer.get(offset).toInt() and 0xFF
                    green += buffer.get(offset + 1).toInt() and 0xFF
                    blue += buffer.get(offset + 2).toInt() and 0xFF
                }
                pixels[destination++] = (red / 9).toByte()
                pixels[destination++] = (green / 9).toByte()
                pixels[destination++] = (blue / 9).toByte()
            }
        }
        return pixels
    }

    private fun homography(c: AnchorPoints): DoubleArray {
        val source = arrayOf(8.0 to 8.0, 152.0 to 8.0, 152.0 to 88.0, 8.0 to 88.0)
        val target = arrayOf(
            c.topLeftX to c.topLeftY, c.topRightX to c.topRightY,
            c.bottomRightX to c.bottomRightY, c.bottomLeftX to c.bottomLeftY
        )
        require(target.all { (x, y) -> x.isFinite() && y.isFinite() }) {
            "Calibration points must be finite"
        }
        val turns = DoubleArray(4) { index ->
            val a = target[index]
            val b = target[(index + 1) % 4]
            val d = target[(index + 2) % 4]
            (b.first - a.first) * (d.second - b.second) -
                (b.second - a.second) * (d.first - b.first)
        }
        require(turns.all { it > 1e-6 } || turns.all { it < -1e-6 }) {
            "Tap the four anchor centers in clockwise order without crossing the points"
        }
        val matrix = Array(8) { DoubleArray(9) }
        for (index in 0..3) {
            val (u, v) = source[index]
            val (x, y) = target[index]
            val row = index * 2
            matrix[row][0] = u; matrix[row][1] = v; matrix[row][2] = 1.0
            matrix[row][6] = -u * x; matrix[row][7] = -v * x; matrix[row][8] = x
            matrix[row + 1][3] = u; matrix[row + 1][4] = v; matrix[row + 1][5] = 1.0
            matrix[row + 1][6] = -u * y; matrix[row + 1][7] = -v * y; matrix[row + 1][8] = y
        }
        return solve(matrix)
    }

    private fun solve(augmented: Array<DoubleArray>): DoubleArray {
        for (column in 0 until 8) {
            var pivot = column
            for (row in column + 1 until 8) {
                if (kotlin.math.abs(augmented[row][column]) > kotlin.math.abs(augmented[pivot][column])) pivot = row
            }
            require(kotlin.math.abs(augmented[pivot][column]) > 1e-10) { "Calibration corners are degenerate" }
            val swap = augmented[column]
            augmented[column] = augmented[pivot]
            augmented[pivot] = swap
            val divisor = augmented[column][column]
            for (item in column..8) augmented[column][item] /= divisor
            for (row in 0 until 8) {
                if (row == column) continue
                val factor = augmented[row][column]
                for (item in column..8) augmented[row][item] -= factor * augmented[column][item]
            }
        }
        return DoubleArray(8) { augmented[it][8] }
    }
}
