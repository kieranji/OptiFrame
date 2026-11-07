package com.kieran.optiframe

import com.kieran.optiframe.protocol.P1ControlBandLayout
import com.kieran.optiframe.ui.optical.P1AnchorDetector
import com.kieran.optiframe.ui.optical.P1PerspectiveSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class P1AnchorDetectorTest {
    @Test
    fun `detects frontal anchors`() {
        val homography = doubleArrayOf(5.0, 0.0, 80.0, 0.0, 5.0, 80.0, 0.0, 0.0)
        val plane = render(homography, includeAnchors = true, seed = 10L)
        val started = System.nanoTime()
        val detection = P1AnchorDetector.detect(plane)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0
        println("P1AnchorDetector 960x640 frontal: %.2f ms".format(elapsedMillis))
        assertNotNull(detection)
        assertDetectionNearTruth(checkNotNull(detection), homography, 0.3)
    }

    @Test
    fun `detects anchors with roll and mild perspective`() {
        val homography = rolledPerspective()
        val detection = P1AnchorDetector.detect(render(homography, includeAnchors = true, seed = 11L))
        assertNotNull(detection)
        assertDetectionNearTruth(checkNotNull(detection), homography, 0.3)
    }

    @Test
    fun `detects anchors with luma noise`() {
        val homography = rolledPerspective()
        val detection = P1AnchorDetector.detect(
            render(homography, includeAnchors = true, seed = 12L, noiseSigma = 8.0)
        )
        assertNotNull(detection)
        assertDetectionNearTruth(checkNotNull(detection), homography, 0.3)
    }

    @Test
    fun `does not detect random cells without anchors`() {
        assertNull(P1AnchorDetector.detect(render(rolledPerspective(), includeAnchors = false, seed = 13L)))
    }

    @Test
    fun `normalized DLT recovers a homography from exact points`() {
        val homography = doubleArrayOf(4.8, -0.3, 91.0, 0.4, 5.1, 40.0, 0.0003, -0.0002)
        val source = listOf(
            0.0 to 0.0, 160.0 to 0.0, 160.0 to 96.0, 0.0 to 96.0,
            25.0 to 17.0, 130.0 to 45.0, 76.0 to 82.0, 95.0 to 4.0
        )
        val destination = source.map { P1PerspectiveSampler.map(homography, it.first, it.second) }
        val fitted = P1AnchorDetector.fitHomography(source, destination)
        for (point in source) {
            val expected = P1PerspectiveSampler.map(homography, point.first, point.second)
            val actual = P1PerspectiveSampler.map(fitted, point.first, point.second)
            assertEquals(expected.first, actual.first, 1e-6)
            assertEquals(expected.second, actual.second, 1e-6)
        }
    }

    private fun assertDetectionNearTruth(
        detection: P1AnchorDetector.Detection,
        homography: DoubleArray,
        maxErrorCells: Double
    ) {
        val anchors = detection.anchors
        val actual = listOf(
            anchors.topLeftX to anchors.topLeftY,
            anchors.topRightX to anchors.topRightY,
            anchors.bottomRightX to anchors.bottomRightY,
            anchors.bottomLeftX to anchors.bottomLeftY
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
            assertTrue(
                "anchor $index error exceeded $maxErrorCells cells",
                hypot(actual[index].first - expected.first, actual[index].second - expected.second) / 5.0 <
                    maxErrorCells
            )
        }
        assertEquals(36, detection.correspondences)
        assertTrue("p95 error was ${detection.p95ErrorCells}", detection.p95ErrorCells < 0.25)
    }

    private fun rolledPerspective(): DoubleArray {
        val angle = Math.toRadians(6.0)
        val scale = 5.0
        return doubleArrayOf(
            scale * cos(angle), -scale * sin(angle), 95.0,
            scale * sin(angle), scale * cos(angle), 45.0,
            0.00012, -0.00006
        )
    }

    private fun render(
        homography: DoubleArray,
        includeAnchors: Boolean,
        seed: Long,
        noiseSigma: Double = 0.0
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
        val inverse = invert(homography)
        val width = 960
        val height = 640
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
