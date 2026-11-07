package com.kieran.optiframe.ui.optical

import androidx.camera.core.ImageProxy
import com.kieran.optiframe.protocol.P1PhysicalLayout
import com.kieran.optiframe.protocol.P1Rgb888GridDecoder

/** Rectifies a CameraX image into the protocol's one-pixel-per-cell RGB grid.
 *
 * Manual section 14.4: map each cell center plus a small sampling grid through
 * the homography into the original image, bilinear-sample the raw planes and
 * average, rather than warping a full 160x96 picture. YUV_420_888 is the
 * preferred input; RGBA_8888 remains supported for compatibility. */
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
        return when {
            image.planes.size == 3 -> sampleYuv420(image, anchors)
            image.planes.size == 1 -> sampleRgba(image, anchors)
            else -> throw IllegalArgumentException(
                "Unsupported camera image: ${image.planes.size} planes"
            )
        }
    }

    /** YUV_420_888 path (manual 14.4/18.3): bilinear sampling on raw planes. */
    private fun sampleYuv420(image: ImageProxy, anchors: AnchorPoints): ByteArray {
        val planes = image.planes
        val yPlane = planeView(planes[0], image.width, image.height)
        val uPlane = planeView(planes[1], image.width / 2, image.height / 2)
        val vPlane = planeView(planes[2], image.width / 2, image.height / 2)
        val pixels = ByteArray(P1PhysicalLayout.GRID_WIDTH * P1PhysicalLayout.GRID_HEIGHT * 3)
        val h = homography(anchors)
        var destination = 0
        for (row in 0 until P1PhysicalLayout.GRID_HEIGHT) {
            for (column in 0 until P1PhysicalLayout.GRID_WIDTH) {
                var luma = 0.0
                var cb = 0.0
                var cr = 0.0
                for (dy in CELL_OFFSETS) for (dx in CELL_OFFSETS) {
                    val (x, y) = map(h, column + 0.5 + dx, row + 0.5 + dy)
                    require(x >= 0.0 && x < image.width && y >= 0.0 && y < image.height) {
                        "Calibration maps P1 cell ($column,$row) outside the camera image"
                    }
                    luma += bilinear(yPlane, x, y)
                    cb += bilinear(uPlane, x / 2.0, y / 2.0)
                    cr += bilinear(vPlane, x / 2.0, y / 2.0)
                }
                val count = (CELL_OFFSETS.size * CELL_OFFSETS.size).toDouble()
                destination = writeRgb(
                    pixels, destination,
                    luma / count, cb / count, cr / count
                )
            }
        }
        return pixels
    }

    /** RGBA_8888 path kept for older analysis configurations and unit tests. */
    private fun sampleRgba(image: ImageProxy, anchors: AnchorPoints): ByteArray {
        val plane = image.planes.single()
        val buffer = plane.buffer.duplicate()
        val pixels = ByteArray(P1PhysicalLayout.GRID_WIDTH * P1PhysicalLayout.GRID_HEIGHT * 3)
        val h = homography(anchors)
        var destination = 0
        for (row in 0 until P1PhysicalLayout.GRID_HEIGHT) {
            for (column in 0 until P1PhysicalLayout.GRID_WIDTH) {
                val (x, y) = map(h, column + 0.5, row + 0.5)
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

    /** Cell-fraction offsets of the per-cell sampling grid (canonical space). */
    private val CELL_OFFSETS = doubleArrayOf(-0.25, 0.0, 0.25)

    /** Maps a canonical grid point through H into image coordinates. */
    internal fun map(h: DoubleArray, u: Double, v: Double): Pair<Double, Double> {
        val denominator = h[6] * u + h[7] * v + 1.0
        require(kotlin.math.abs(denominator) > 1e-9) { "Invalid projective transform" }
        return (h[0] * u + h[1] * v + h[2]) / denominator to
            (h[3] * u + h[4] * v + h[5]) / denominator
    }

    /** Flat view of one ImageProxy plane so sampling can be unit-tested. */
    internal data class PlaneView(
        val data: ByteArray,
        val rowStride: Int,
        val pixelStride: Int,
        val width: Int,
        val height: Int
    )

    private fun planeView(plane: ImageProxy.PlaneProxy, width: Int, height: Int): PlaneView {
        val buffer = plane.buffer.duplicate()
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        return PlaneView(data, plane.rowStride, plane.pixelStride, width, height)
    }

    /** Bilinear-interpolated sample of a plane at fractional coordinates. */
    internal fun bilinear(plane: PlaneView, x: Double, y: Double): Double {
        val cx = x.coerceIn(0.0, plane.width - 1.0)
        val cy = y.coerceIn(0.0, plane.height - 1.0)
        val x0 = cx.toInt()
        val y0 = cy.toInt()
        val x1 = (x0 + 1).coerceAtMost(plane.width - 1)
        val y1 = (y0 + 1).coerceAtMost(plane.height - 1)
        val fx = cx - x0
        val fy = cy - y0
        fun at(px: Int, py: Int): Double {
            val offset = py * plane.rowStride + px * plane.pixelStride
            require(offset >= 0 && offset < plane.data.size) { "Plane layout exceeds its buffer" }
            return (plane.data[offset].toInt() and 0xFF).toDouble()
        }
        val top = at(x0, y0) + (at(x1, y0) - at(x0, y0)) * fx
        val bottom = at(x0, y1) + (at(x1, y1) - at(x0, y1)) * fx
        return top + (bottom - top) * fy
    }

    /** JPEG-range YCbCr to RGB, written into [pixels] at [offset]; returns offset + 3. */
    internal fun writeRgb(pixels: ByteArray, offset: Int, y: Double, u: Double, v: Double): Int {
        fun clamp(value: Double) = value.coerceIn(0.0, 255.0).toInt().toByte()
        pixels[offset] = clamp(y + 1.402 * (v - 128.0))
        pixels[offset + 1] = clamp(y - 0.344136 * (u - 128.0) - 0.714136 * (v - 128.0))
        pixels[offset + 2] = clamp(y + 1.772 * (u - 128.0))
        return offset + 3
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
