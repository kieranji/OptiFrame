package com.kieran.optiframe.ui.optical

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kieran.optiframe.protocol.P1M3SenderSession
import com.kieran.optiframe.protocol.P1RenderedLogicalFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class P1Safe30DisplayState {
    DISPLAYING,
    WAITING_FOR_FEEDBACK,
    PAUSED,
    CANCELLED,
    COMPLETE
}

/** Drives SAFE30 or FAST60 on the Compose frame clock and retains its last frame. */
@Composable
fun P1OpticalDisplay(
    session: P1M3SenderSession,
    modifier: Modifier = Modifier,
    onStateChange: (P1Safe30DisplayState) -> Unit = {}
) {
    val initialFrame = remember(session) { session.lastPresentation?.physicalFrame }
    var currentFrame by remember(session) { mutableStateOf(initialFrame) }
    val reportState by rememberUpdatedState(onStateChange)

    LaunchedEffect(session) {
        var lastReportedState: P1Safe30DisplayState? = null
        fun report(state: P1Safe30DisplayState) {
            if (state != lastReportedState) {
                lastReportedState = state
                reportState(state)
            }
        }

        while (true) {
            if (session.isCancelled) {
                report(P1Safe30DisplayState.CANCELLED)
                break
            }
            if (if (session.fast60) session.receiverReportedComplete else session.progress.isComplete) {
                report(P1Safe30DisplayState.COMPLETE)
                break
            }

            // Encode a frame away from the UI thread, then commit it on the
            // next frame-clock tick. Feedback can arrive from another thread.
            val nextFrame: Pair<P1RenderedLogicalFrame, P1Safe30DisplayState>? =
                withContext(Dispatchers.Default) {
                    session.nextVsyncPresentation()?.let { presentation ->
                        presentation.physicalFrame to P1Safe30DisplayState.DISPLAYING
                    }
                }
            withFrameNanos { }

            if (nextFrame != null) {
                currentFrame = nextFrame.first
                report(nextFrame.second)
            } else if (session.fast60 && session.receiverReportedComplete ||
                !session.fast60 && session.progress.isComplete
            ) {
                report(P1Safe30DisplayState.COMPLETE)
                break
            } else {
                report(if (session.isPaused) P1Safe30DisplayState.PAUSED else P1Safe30DisplayState.WAITING_FOR_FEEDBACK)
            }
        }
    }

    val frame = currentFrame
    if (frame == null) {
        Box(modifier.fillMaxSize().background(Color.Black))
    } else {
        P1FrameDisplay(frame = frame, modifier = modifier)
    }
}
