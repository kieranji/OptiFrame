package com.kieran.optiframe.protocol

/** Receive outcome with optional feedback ready for the sender. */
data class RaptorQFileReceiveEvent(
    val receiveResult: RaptorQBlockReceiveResult,
    val feedback: RaptorQBlockFeedback?
)
