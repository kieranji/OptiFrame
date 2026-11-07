package com.kieran.optiframe

import com.kieran.optiframe.protocol.P1ControlBandLayout
import com.kieran.optiframe.protocol.Of8Palette
import com.kieran.optiframe.ui.optical.P1AnchorDetector
import com.kieran.optiframe.ui.optical.P1PerspectiveSampler
import org.junit.Assert.assertTrue
import java.util.Random
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal object P1SyntheticFrames {
    fun frontalHomography(
        scale: Double = 5.0,
        offsetX: Double = 80.0,
        offsetY: Double = 80.0
    ): DoubleArray =
        doubleArrayOf(scale, 0.0, offsetX, 0.0, scale, offsetY, 0.0, 0.0)

    fun composeHomographies(outer: DoubleArray, inner: DoubleArray): DoubleArray {
        val first = arrayOf(
            doubleArrayOf(outer[0], outer[1], outer[2]),
            doubleArrayOf(outer[3], outer[4], outer[5]),
            doubleArrayOf(outer[6], outer[7], 1.0)
        )
        val second = arrayOf(
            doubleArrayOf(inner[0], inner[1], inner[2]),
            doubleArrayOf(inner[3], inner[4], inner[5]),
            doubleArrayOf(inner[6], inner[7], 1.0)
        )
        val composed = Array(3) { row ->
            DoubleArray(3) { column ->
                (0..2).sumOf { first[row][it] * second[it][column] }
            }
        }
        val scale = composed[2][2]
        return doubleArrayOf(
            composed[0][0] / scale, composed[0][1] / scale, composed[0][2] / scale,
            composed[1][0] / scale, composed[1][1] / scale, composed[1][2] / scale,
            composed[2][0] / scale, composed[2][1] / scale
        )
    }

    fun rolledPerspective(): DoubleArray {
        val angle = Math.toRadians(6.0)
        val scale = 5.0
        return doubleArrayOf(
            scale * cos(angle), -scale * sin(angle), 95.0,
            scale * sin(angle), scale * cos(angle), 45.0,
            0.00012, -0.00006
        )
    }

    fun translatedAndRolledFrontal(): DoubleArray {
        val angle = Math.toRadians(2.0)
        val cosine = cos(angle)
        val sine = sin(angle)
        return doubleArrayOf(
            5.0 * cosine, -5.0 * sine, 485.0 - 400.0 * cosine + 240.0 * sine,
            5.0 * sine, 5.0 * cosine, 325.0 - 400.0 * sine - 240.0 * cosine,
            0.0, 0.0
        )
    }

    fun render(
        homography: DoubleArray,
        includeAnchors: Boolean,
        seed: Long,
        noiseSigma: Double = 0.0,
        includePilots: Boolean = false,
        canvasWidth: Int = 960,
        canvasHeight: Int = 640
    ): P1PerspectiveSampler.PlaneView {
        val random = Random(seed)
        val labels = IntArray(160 * 96) {
            intArrayOf(32, 64, 96, 128, 160, 192, 224)[random.nextInt(7)]
        }
        if (includeAnchors) {
            for (rect in P1ControlBandLayout.anchors) {
                for (localY in 0 until rect.height) for (localX in 0 until rect.width) {
                    labels[(rect.y + localY) * 160 + rect.x + localX] =
                        if (P1ControlBandLayout.anchorLabel(localX, localY) == 0) 16 else 235
                }
            }
        }
        if (includePilots) {
            for (rect in listOf(P1ControlBandLayout.leftPilots, P1ControlBandLayout.rightPilots)) {
                for (localY in 0 until rect.height) for (localX in 0 until rect.width) {
                    val color = Of8Palette.symbolToColor(
                        P1ControlBandLayout.pilotLabel(localX, localY)
                    )
                    labels[(rect.y + localY) * 160 + rect.x + localX] =
                        (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue).roundToInt()
                }
            }
        }
        val inverse = invert(homography)
        val width = canvasWidth
        val height = canvasHeight
        val data = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val (u, v) = P1PerspectiveSampler.map(inverse, x.toDouble(), y.toDouble())
            var value = if (u >= 0.0 && u < 160.0 && v >= 0.0 && v < 96.0) {
                labels[v.toInt() * 160 + u.toInt()]
            } else {
                128
            }
            if (noiseSigma > 0.0) {
                value = (value + random.nextGaussian() * noiseSigma).toInt().coerceIn(0, 255)
            }
            data[y * width + x] = value.toByte()
        }
        return P1PerspectiveSampler.PlaneView(data, width, 1, width, height)
    }

    fun assertAnchorsNearTruth(
        detection: P1AnchorDetector.Detection,
        homography: DoubleArray,
        maxErrorCells: Double,
        pixelsPerCell: Double = 5.0
    ) {
        val actual = listOf(
            detection.anchors.topLeftX to detection.anchors.topLeftY,
            detection.anchors.topRightX to detection.anchors.topRightY,
            detection.anchors.bottomRightX to detection.anchors.bottomRightY,
            detection.anchors.bottomLeftX to detection.anchors.bottomLeftY
        )
        val orderedRects = listOf(
            P1ControlBandLayout.anchors[0],
            P1ControlBandLayout.anchors[1],
            P1ControlBandLayout.anchors[3],
            P1ControlBandLayout.anchors[2]
        )
        for (index in orderedRects.indices) {
            val rect = orderedRects[index]
            val expected = P1PerspectiveSampler.map(
                homography, rect.x + rect.width / 2.0, rect.y + rect.height / 2.0
            )
            val errorCells = kotlin.math.hypot(
                actual[index].first - expected.first,
                actual[index].second - expected.second
            ) / pixelsPerCell
            assertTrue(
                "anchor $index error $errorCells exceeded $maxErrorCells cells",
                errorCells < maxErrorCells
            )
        }
    }

    private fun invert(h: DoubleArray): DoubleArray {
        val a = h[0]; val b = h[1]; val c = h[2]
        val d = h[3]; val e = h[4]; val f = h[5]
        val g = h[6]; val i = h[7]
        val determinant = a * (e - f * i) - b * (d - f * g) + c * (d * i - e * g)
        val inverse = arrayOf(
            doubleArrayOf(e - f * i, c * i - b, b * f - c * e),
            doubleArrayOf(f * g - d, a - c * g, c * d - a * f),
            doubleArrayOf(d * i - e * g, b * g - a * i, a * e - b * d)
        )
        return doubleArrayOf(
            inverse[0][0] / inverse[2][2], inverse[0][1] / inverse[2][2], inverse[0][2] / inverse[2][2],
            inverse[1][0] / inverse[2][2], inverse[1][1] / inverse[2][2], inverse[1][2] / inverse[2][2],
            inverse[2][0] / inverse[2][2], inverse[2][1] / inverse[2][2]
        ).also { check(determinant != 0.0) }
    }
}
