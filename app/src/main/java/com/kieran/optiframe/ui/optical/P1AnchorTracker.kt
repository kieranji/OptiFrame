package com.kieran.optiframe.ui.optical

/** Tracks a previous anchor lock and falls back to full relocalization when needed. */
internal class P1AnchorTracker(
    private val maxConsecutiveDecodeFailures: Int = DEFAULT_MAX_CONSECUTIVE_DECODE_FAILURES
) {
    var trackedFrames = 0L
        private set
    var fullDetections = 0L
        private set

    private var locked: P1AnchorDetector.Detection? = null
    private var consecutiveDecodeFailures = 0

    @get:Synchronized
    val isLocked: Boolean
        get() = locked != null

    init {
        require(maxConsecutiveDecodeFailures > 0)
    }

    @Synchronized
    fun locate(luma: P1PerspectiveSampler.PlaneView): P1AnchorDetector.Detection? {
        locked?.let { previous ->
            val tracked = P1AnchorDetector.track(luma, previous)
            if (tracked != null) {
                trackedFrames++
                locked = tracked
                return tracked
            }
        }

        fullDetections++
        val detected = P1AnchorDetector.detect(luma)
        if (detected != null) {
            locked = detected
            consecutiveDecodeFailures = 0
            return detected
        }
        return locked
    }

    @Synchronized
    fun onDecodeResult(success: Boolean) {
        if (success) {
            consecutiveDecodeFailures = 0
        } else {
            consecutiveDecodeFailures++
            if (consecutiveDecodeFailures >= maxConsecutiveDecodeFailures) {
                locked = null
                consecutiveDecodeFailures = 0
            }
        }
    }

    @Synchronized
    fun reset() {
        locked = null
        consecutiveDecodeFailures = 0
        trackedFrames = 0L
        fullDetections = 0L
    }

    companion object {
        const val DEFAULT_MAX_CONSECUTIVE_DECODE_FAILURES = 3
    }
}
