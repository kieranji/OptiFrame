package com.kieran.optiframe

import com.kieran.optiframe.ui.optical.P1AnchorDetector
import com.kieran.optiframe.ui.optical.P1AnchorTracker
import com.kieran.optiframe.ui.optical.P1PerspectiveSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.hypot

class P1AnchorTrackerTest {
    @Test
    fun `track follows translation and extra roll`() {
        val previousHomography = P1SyntheticFrames.frontalHomography()
        val nextHomography = P1SyntheticFrames.translatedAndRolledFrontal()
        val previous = detect(previousHomography, seed = 20L)

        val tracked = P1AnchorDetector.track(
            P1SyntheticFrames.render(nextHomography, includeAnchors = true, seed = 20L),
            previous
        )

        assertNotNull(tracked)
        val detection = checkNotNull(tracked)
        P1SyntheticFrames.assertAnchorsNearTruth(detection, nextHomography, 0.3)
        assertTrue(detection.p95ErrorCells <= P1AnchorDetector.MAX_P95_ERROR_CELLS)
    }

    @Test
    fun `track returns null when anchors are lost`() {
        val homography = P1SyntheticFrames.frontalHomography()
        val previous = detect(homography, seed = 21L)

        assertNull(
            P1AnchorDetector.track(
                P1SyntheticFrames.render(homography, includeAnchors = false, seed = 22L),
                previous
            )
        )
    }

    @Test
    fun `track preserves top left anchor order`() {
        val homography = P1SyntheticFrames.translatedAndRolledFrontal()
        val previous = detect(P1SyntheticFrames.frontalHomography(), seed = 23L)
        val tracked = checkNotNull(
            P1AnchorDetector.track(
                P1SyntheticFrames.render(homography, includeAnchors = true, seed = 23L),
                previous
            )
        )
        val expectedTopLeft = P1PerspectiveSampler.map(homography, 8.0, 8.0)
        val actualTopLeft = tracked.anchors.topLeftX to tracked.anchors.topLeftY
        val otherAnchors = listOf(
            tracked.anchors.topRightX to tracked.anchors.topRightY,
            tracked.anchors.bottomRightX to tracked.anchors.bottomRightY,
            tracked.anchors.bottomLeftX to tracked.anchors.bottomLeftY
        )

        assertTrue(
            hypot(actualTopLeft.first - expectedTopLeft.first, actualTopLeft.second - expectedTopLeft.second) <
                otherAnchors.minOf { hypot(it.first - expectedTopLeft.first, it.second - expectedTopLeft.second) }
        )
    }

    @Test
    fun `first locate detects and following frame tracks`() {
        val tracker = P1AnchorTracker()
        val firstHomography = P1SyntheticFrames.frontalHomography()
        val nextHomography = P1SyntheticFrames.translatedAndRolledFrontal()

        assertNotNull(
            tracker.locate(P1SyntheticFrames.render(firstHomography, includeAnchors = true, seed = 24L))
        )
        assertEquals(1L, tracker.fullDetections)
        assertEquals(0L, tracker.trackedFrames)

        assertNotNull(
            tracker.locate(P1SyntheticFrames.render(nextHomography, includeAnchors = true, seed = 24L))
        )
        assertEquals(1L, tracker.fullDetections)
        assertEquals(1L, tracker.trackedFrames)
    }

    @Test
    fun `locate returns stale lock when tracking and detection fail`() {
        val tracker = P1AnchorTracker()
        val homography = P1SyntheticFrames.frontalHomography()
        val original = checkNotNull(
            tracker.locate(P1SyntheticFrames.render(homography, includeAnchors = true, seed = 25L))
        )

        val fallback = tracker.locate(
            P1SyntheticFrames.render(homography, includeAnchors = false, seed = 26L)
        )

        assertNotNull(fallback)
        assertEquals(original.anchors, checkNotNull(fallback).anchors)
        assertEquals(2L, tracker.fullDetections)
        assertEquals(0L, tracker.trackedFrames)

        assertNotNull(
            tracker.locate(
                P1SyntheticFrames.render(
                    P1SyntheticFrames.translatedAndRolledFrontal(),
                    includeAnchors = true,
                    seed = 25L
                )
            )
        )
        assertEquals(2L, tracker.fullDetections)
        assertEquals(1L, tracker.trackedFrames)
    }

    @Test
    fun `three decode failures drop lock and next locate detects again`() {
        val tracker = P1AnchorTracker()
        val firstHomography = P1SyntheticFrames.frontalHomography()
        tracker.locate(P1SyntheticFrames.render(firstHomography, includeAnchors = true, seed = 27L))

        repeat(3) { tracker.onDecodeResult(false) }
        assertNotNull(
            tracker.locate(
                P1SyntheticFrames.render(
                    P1SyntheticFrames.translatedAndRolledFrontal(),
                    includeAnchors = true,
                    seed = 27L
                )
            )
        )
        assertEquals(2L, tracker.fullDetections)
        assertEquals(0L, tracker.trackedFrames)

        val recoveredTracker = P1AnchorTracker()
        recoveredTracker.locate(
            P1SyntheticFrames.render(firstHomography, includeAnchors = true, seed = 28L)
        )
        recoveredTracker.onDecodeResult(false)
        recoveredTracker.onDecodeResult(false)
        recoveredTracker.onDecodeResult(true)
        recoveredTracker.onDecodeResult(false)
        recoveredTracker.onDecodeResult(false)

        assertNotNull(
            recoveredTracker.locate(
                P1SyntheticFrames.render(
                    P1SyntheticFrames.translatedAndRolledFrontal(),
                    includeAnchors = true,
                    seed = 28L
                )
            )
        )
        assertEquals(1L, recoveredTracker.fullDetections)
        assertEquals(1L, recoveredTracker.trackedFrames)
    }

    @Test
    fun `prints mean detection and tracking timings`() {
        val homography = P1SyntheticFrames.frontalHomography()
        val frame = P1SyntheticFrames.render(homography, includeAnchors = true, seed = 29L)
        val previous = detect(homography, seed = 29L)
        val detectMillis = DoubleArray(10) {
            val started = System.nanoTime()
            assertNotNull(P1AnchorDetector.detect(frame))
            (System.nanoTime() - started) / 1_000_000.0
        }
        val trackMillis = DoubleArray(10) {
            val started = System.nanoTime()
            assertNotNull(P1AnchorDetector.track(frame, previous))
            (System.nanoTime() - started) / 1_000_000.0
        }
        val detectMs = detectMillis.average()
        val trackMs = trackMillis.average()

        println(
            "P1AnchorTracker 960x640 mean: detect=%.2f ms, track=%.2f ms".format(
                Locale.US, detectMs, trackMs
            )
        )
        assertTrue(trackMs < detectMs / 2.0)
    }

    private fun detect(homography: DoubleArray, seed: Long) = checkNotNull(
        P1AnchorDetector.detect(
            P1SyntheticFrames.render(homography, includeAnchors = true, seed = seed)
        )
    )
}
