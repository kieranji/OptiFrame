package com.kieran.optiframe.ui.optical

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.kieran.optiframe.protocol.P1ControlBandLayout
import com.kieran.optiframe.protocol.P1PhysicalLayout
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.hypot
import kotlin.math.sqrt

internal data class P1ReceiverDiagnosticArchive(
    val jpeg: ByteArray,
    val gridPng: ByteArray?,
    val info: String
) {
    fun writeTo(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("frame.jpg"))
            zip.write(jpeg)
            zip.closeEntry()
            gridPng?.let {
                zip.putNextEntry(ZipEntry("grid.png"))
                zip.write(it)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("info.txt"))
            zip.write(info.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}

internal object P1ReceiverDiagnostics {
    private const val GRID_SCALE = 4

    fun line(
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        anchors: P1PerspectiveSampler.AnchorPoints?,
        rgb888: ByteArray?,
        orientationScore: Double?,
        sampleError: String? = null
    ): String {
        val pixelsPerCell = anchors?.let {
            (
                hypot(it.topRightX - it.topLeftX, it.topRightY - it.topLeftY) +
                    hypot(it.bottomRightX - it.bottomLeftX, it.bottomRightY - it.bottomLeftY)
                ) / 2.0 / 144.0
        }
        val metrics = rgb888?.let(::measureGrid)
        val pxText = pixelsPerCell?.let { "%.2f".format(Locale.US, it) } ?: "n/a"
        val orientationText = orientationScore?.let { "%.3f".format(Locale.US, it) } ?: "n/a"
        val separationText = metrics?.separation?.let { "%.1f".format(Locale.US, it) } ?: "n/a"
        val leftHeaderText = metrics?.leftBinaryPercent?.let { "%.1f".format(Locale.US, it) } ?: "n/a"
        val rightHeaderText = metrics?.rightBinaryPercent?.let { "%.1f".format(Locale.US, it) } ?: "n/a"
        return buildString {
            append(
                "px/cell=$pxText · orient r=$orientationText · pilot sep=$separationText" +
                    " · hdr bw=L$leftHeaderText%/R$rightHeaderText% · " +
                    "${imageWidth}×$imageHeight rot=${rotationDegrees}°"
            )
            sampleError?.let { append(" · sample error: $it") }
        }
    }

    fun yuvToJpeg(image: ImageProxy): ByteArray {
        require(image.planes.size == 3) { "Expected a YUV_420_888 camera frame" }
        require(image.width % 2 == 0 && image.height % 2 == 0) {
            "YUV frame dimensions must be even"
        }
        val planes = image.planes
        val yBuffer = planes[0].buffer.duplicate()
        val uBuffer = planes[1].buffer.duplicate()
        val vBuffer = planes[2].buffer.duplicate()
        val yBase = yBuffer.position()
        val uBase = uBuffer.position()
        val vBase = vBuffer.position()
        val ySize = image.width * image.height
        val nv21 = ByteArray(ySize + ySize / 2)
        var outputOffset = 0
        for (row in 0 until image.height) {
            for (column in 0 until image.width) {
                nv21[outputOffset++] = planeByte(
                    yBuffer, yBase, planes[0].rowStride, planes[0].pixelStride, column, row
                )
            }
        }
        for (row in 0 until image.height / 2) {
            for (column in 0 until image.width / 2) {
                nv21[outputOffset++] = planeByte(
                    vBuffer, vBase, planes[2].rowStride, planes[2].pixelStride, column, row
                )
                nv21[outputOffset++] = planeByte(
                    uBuffer, uBase, planes[1].rowStride, planes[1].pixelStride, column, row
                )
            }
        }
        val output = ByteArrayOutputStream()
        val compressed = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            .compressToJpeg(Rect(0, 0, image.width, image.height), 92, output)
        check(compressed) { "Could not encode diagnostic JPEG" }
        return output.toByteArray()
    }

    fun gridToPng(rgb888: ByteArray): ByteArray {
        require(rgb888.size == P1PhysicalLayout.GRID_WIDTH * P1PhysicalLayout.GRID_HEIGHT * 3)
        val width = P1PhysicalLayout.GRID_WIDTH
        val height = P1PhysicalLayout.GRID_HEIGHT
        val pixels = IntArray(width * height)
        for (index in pixels.indices) {
            val offset = index * 3
            val red = rgb888[offset].toInt() and 0xFF
            val green = rgb888[offset + 1].toInt() and 0xFF
            val blue = rgb888[offset + 2].toInt() and 0xFF
            pixels[index] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val scaled = Bitmap.createScaledBitmap(
            bitmap, width * GRID_SCALE, height * GRID_SCALE, false
        )
        return try {
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode diagnostic RGB grid"
                }
                output.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    private data class GridMetrics(
        val separation: Double,
        val leftBinaryPercent: Double,
        val rightBinaryPercent: Double
    )

    private fun measureGrid(rgb888: ByteArray): GridMetrics {
        require(rgb888.size == P1PhysicalLayout.GRID_WIDTH * P1PhysicalLayout.GRID_HEIGHT * 3)
        val sums = Array(8) { DoubleArray(3) }
        val counts = IntArray(8)
        for (rect in listOf(P1ControlBandLayout.leftPilots, P1ControlBandLayout.rightPilots)) {
            for (localY in 0 until rect.height) {
                for (localX in 0 until rect.width) {
                    val label = P1ControlBandLayout.pilotLabel(localX, localY)
                    val offset = ((rect.y + localY) * P1PhysicalLayout.GRID_WIDTH +
                        rect.x + localX) * 3
                    for (channel in 0..2) {
                        sums[label][channel] += rgb888[offset + channel].toInt() and 0xFF
                    }
                    counts[label]++
                }
            }
        }
        val palette = Array(8) { label ->
            require(counts[label] > 0)
            DoubleArray(3) { channel -> sums[label][channel] / counts[label] }
        }
        var minSeparationSquared = Double.POSITIVE_INFINITY
        for (first in palette.indices) {
            for (second in first + 1 until palette.size) {
                minSeparationSquared = minOf(
                    minSeparationSquared,
                    distanceSquared(palette[first], palette[second])
                )
            }
        }
        return GridMetrics(
            separation = sqrt(minSeparationSquared),
            leftBinaryPercent = binaryHeaderPercent(rgb888, palette, P1ControlBandLayout.leftHeader),
            rightBinaryPercent = binaryHeaderPercent(rgb888, palette, P1ControlBandLayout.rightHeader)
        )
    }

    private fun binaryHeaderPercent(
        rgb888: ByteArray,
        palette: Array<DoubleArray>,
        rect: P1ControlBandLayout.Rect
    ): Double {
        var binaryCount = 0
        for (localY in 0 until rect.height) {
            for (localX in 0 until rect.width) {
                val offset = ((rect.y + localY) * P1PhysicalLayout.GRID_WIDTH +
                    rect.x + localX) * 3
                val color = doubleArrayOf(
                    (rgb888[offset].toInt() and 0xFF).toDouble(),
                    (rgb888[offset + 1].toInt() and 0xFF).toDouble(),
                    (rgb888[offset + 2].toInt() and 0xFF).toDouble()
                )
                val nearest = palette.indices.minByOrNull {
                    distanceSquared(color, palette[it])
                } ?: continue
                if (nearest == 0 || nearest == 7) binaryCount++
            }
        }
        return binaryCount * 100.0 / (rect.width * rect.height)
    }

    private fun distanceSquared(first: DoubleArray, second: DoubleArray): Double {
        var result = 0.0
        for (channel in 0..2) {
            val difference = first[channel] - second[channel]
            result += difference * difference
        }
        return result
    }

    private fun planeByte(
        buffer: ByteBuffer,
        base: Int,
        rowStride: Int,
        pixelStride: Int,
        column: Int,
        row: Int
    ): Byte {
        val offset = base + row * rowStride + column * pixelStride
        require(offset >= 0 && offset < buffer.limit()) { "YUV plane layout exceeds its buffer" }
        return buffer.get(offset)
    }
}
