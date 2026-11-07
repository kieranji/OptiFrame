package com.kieran.optiframe.protocol

/** Presents one unique P1 frame on every display refresh opportunity. */
class P1Fast60FrameScheduler(
    private val frameSender: P1RaptorQFrameSender,
    firstFrameSequence: Long
) : P1FramePresentationScheduler {
    private var nextFrameSequence = firstFrameSequence

    override var lastPresentation: P1Safe30Presentation? = null
        private set

    init {
        require(firstFrameSequence in 0..P1Safe30FrameScheduler.MAX_FRAME_SEQUENCE)
    }

    override fun nextPresentation(): P1Safe30Presentation? {
        val sequence = nextFrameSequence
        val packed = frameSender.nextFrame(sequence) ?: return null
        nextFrameSequence = if (sequence == P1Safe30FrameScheduler.MAX_FRAME_SEQUENCE) 0L else sequence + 1L
        return P1Safe30Presentation(
            frame = packed.frame,
            frameSequence = sequence,
            repeatOrdinal = 0
        ).also { lastPresentation = it }
    }
}
