package com.kieran.optiframe.protocol

/** File-layer outcomes for every decoded symbol in one optical frame. */
data class RaptorQOpticalFrameReceiveResult(
    val symbolResults: List<RaptorQBlockReceiveResult>,
    val slotFailures: List<P1SlotDecodeFailure>,
    val feedback: RaptorQBlockFeedback?
)
