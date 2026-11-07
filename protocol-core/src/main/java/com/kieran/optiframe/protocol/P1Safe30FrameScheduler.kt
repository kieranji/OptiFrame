package com.kieran.optiframe.protocol

/** One display opportunity in SAFE30 mode; ordinal is zero-based. */
data class P1Safe30Presentation(
    val frame: P1LogicalOpticalFrame,
    val frameSequence: Long,
    val repeatOrdinal: Int
) {
    /** Fully composed 160×96 labels for a renderer to map onto screen cells. */
    val physicalFrame: P1RenderedLogicalFrame by lazy(LazyThreadSafetyMode.PUBLICATION) {
        P1PhysicalFrameRenderer.render(frame)
    }

    val isRepeatedPresentation: Boolean
        get() = repeatOrdinal > 0
}

/**
 * Presents each unique logical P1 frame for two consecutive display refreshes.
 * Call once per VSYNC opportunity; this class does not schedule wall-clock time.
 */
class P1Safe30FrameScheduler(
    private val frameSender: P1RaptorQFrameSender,
    firstFrameSequence: Long
) : P1FramePresentationScheduler {
    private var nextFrameSequence = firstFrameSequence
    private var pendingFrame: P1PackedRaptorQFrame? = null
    private var presentationsRemaining = 0

    override var lastPresentation: P1Safe30Presentation? = null
        private set

    init {
        require(firstFrameSequence in 0..MAX_FRAME_SEQUENCE)
    }

    /**
     * Returns null while waiting for block feedback or after completion. The
     * renderer should keep displaying [lastPresentation] when this returns null.
     */
    override fun nextPresentation(): P1Safe30Presentation? {
        if (pendingFrame == null) {
            val packed = frameSender.nextFrame(nextFrameSequence) ?: return null
            require(packed.frame.header.frameSequence == nextFrameSequence) {
                "Packed frame sequence does not match the requested SAFE30 sequence"
            }
            pendingFrame = packed
            presentationsRemaining = PRESENTATIONS_PER_FRAME
            nextFrameSequence = if (nextFrameSequence == MAX_FRAME_SEQUENCE) {
                0L
            } else {
                nextFrameSequence + 1L
            }
        }

        val packed = checkNotNull(pendingFrame)
        val ordinal = PRESENTATIONS_PER_FRAME - presentationsRemaining
        presentationsRemaining--
        if (presentationsRemaining == 0) {
            pendingFrame = null
        }
        val presentation = P1Safe30Presentation(
            frame = packed.frame,
            frameSequence = packed.frame.header.frameSequence,
            repeatOrdinal = ordinal
        )
        lastPresentation = presentation
        return presentation
    }

    companion object {
        const val PRESENTATIONS_PER_FRAME: Int = 2
        const val MAX_FRAME_SEQUENCE: Long = 0xFFFF_FFFFL
    }
}
