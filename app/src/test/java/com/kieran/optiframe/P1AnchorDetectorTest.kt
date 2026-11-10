package com.kieran.optiframe

import com.kieran.optiframe.ui.optical.P1AnchorDetector
import com.kieran.optiframe.ui.optical.P1PerspectiveSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P1AnchorDetectorTest {
    @Test
    fun `detects frontal anchors`() {
        val homography = P1SyntheticFrames.frontalHomography()
        val plane = P1SyntheticFrames.render(homography, includeAnchors = true, seed = 10L)
        val started = System.nanoTime()
        val detection = P1AnchorDetector.detect(plane)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0
        println("P1AnchorDetector 960x640 frontal: %.2f ms".format(elapsedMillis))
        assertNotNull(detection)
        assertDetectionNearTruth(checkNotNull(detection), homography, 0.3)
    }

    @Test
    fun `detects anchors with roll and mild perspective`() {
        val homography = P1SyntheticFrames.rolledPerspective()
        val detection = P1AnchorDetector.detect(
            P1SyntheticFrames.render(homography, includeAnchors = true, seed = 11L)
        )
        assertNotNull(detection)
        assertDetectionNearTruth(checkNotNull(detection), homography, 0.3)
    }

    @Test
    fun `detects anchors with luma noise`() {
        val homography = P1SyntheticFrames.rolledPerspective()
        val detection = P1AnchorDetector.detect(
            P1SyntheticFrames.render(homography, includeAnchors = true, seed = 12L, noiseSigma = 8.0)
        )
        assertNotNull(detection)
        assertDetectionNearTruth(checkNotNull(detection), homography, 0.3)
    }

    @Test
    fun `detects 180 degree rotation using pilot orientation`() {
        val homography = P1SyntheticFrames.composeHomographies(
            P1SyntheticFrames.frontalHomography(),
            doubleArrayOf(-1.0, 0.0, 160.0, 0.0, -1.0, 96.0, 0.0, 0.0)
        )
        val detection = P1AnchorDetector.detect(
            P1SyntheticFrames.render(
                homography,
                includeAnchors = true,
                includePilots = true,
                seed = 15L
            )
        )

        assertNotNull(detection)
        P1SyntheticFrames.assertAnchorsNearTruth(checkNotNull(detection), homography, 0.3)
    }

    @Test
    fun `detects 90 degree rotation in portrait buffer`() {
        val homography = P1SyntheticFrames.composeHomographies(
            P1SyntheticFrames.frontalHomography(scale = 4.0, offsetX = 100.0, offsetY = 100.0),
            doubleArrayOf(0.0, -1.0, 96.0, 1.0, 0.0, 0.0, 0.0, 0.0)
        )
        val detection = P1AnchorDetector.detect(
            P1SyntheticFrames.render(
                homography,
                includeAnchors = true,
                includePilots = true,
                seed = 16L,
                canvasWidth = 600,
                canvasHeight = 800
            )
        )

        assertNotNull(detection)
        P1SyntheticFrames.assertAnchorsNearTruth(
            checkNotNull(detection),
            homography,
            maxErrorCells = 0.3,
            pixelsPerCell = 4.0
        )
    }

    @Test
    fun `does not detect anchors arranged as a square`() {
        val squareHomography = doubleArrayOf(5.0, 0.0, 80.0, 0.0, 9.0, 80.0, 0.0, 0.0)

        assertNull(
            P1AnchorDetector.detect(
                P1SyntheticFrames.render(
                    squareHomography,
                    includeAnchors = true,
                    seed = 17L,
                    canvasHeight = 960
                )
            )
        )
    }

    @Test
    fun `does not detect random cells without anchors`() {
        assertNull(
            P1AnchorDetector.detect(
                P1SyntheticFrames.render(
                    P1SyntheticFrames.rolledPerspective(), includeAnchors = false, seed = 13L
                )
            )
        )
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
        P1SyntheticFrames.assertAnchorsNearTruth(detection, homography, maxErrorCells)
        assertEquals(36, detection.correspondences)
        assertTrue("p95 error was ${detection.p95ErrorCells}", detection.p95ErrorCells < 0.25)
    }
}
