package com.kieran.optiframe.protocol

/** Supplies the next display opportunity while keeping the most recent frame available. */
interface P1FramePresentationScheduler {
    val lastPresentation: P1Safe30Presentation?
    fun nextPresentation(): P1Safe30Presentation?
}
