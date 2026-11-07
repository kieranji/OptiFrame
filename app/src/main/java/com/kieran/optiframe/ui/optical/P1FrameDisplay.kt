package com.kieran.optiframe.ui.optical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kieran.optiframe.protocol.Of8Palette
import com.kieran.optiframe.protocol.P1RenderedLogicalFrame

/** Draws one complete P1 physical frame with sharp cell boundaries. */
@Composable
fun P1FrameDisplay(
    frame: P1RenderedLogicalFrame,
    modifier: Modifier = Modifier
) {
    val labels = remember(frame) { frame.labelsCopy() }
    val palette = remember {
        Array(8) { label ->
            Of8Palette.symbolToColor(label).let { rgb ->
                Color(rgb.red, rgb.green, rgb.blue)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = "OptiFrame P1 SAFE30 optical frame" }
    ) {
        val scale = minOf(size.width / frame.width, size.height / frame.height)
        val frameWidth = frame.width * scale
        val frameHeight = frame.height * scale
        val originX = (size.width - frameWidth) / 2f
        val originY = (size.height - frameHeight) / 2f
        val cellSize = Size(scale, scale)

        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val rawLabel = labels[y * frame.width + x]
                val label = if (rawLabel in 0..7) rawLabel else 0
                drawRect(
                    color = palette[label],
                    topLeft = Offset(originX + x * scale, originY + y * scale),
                    size = cellSize
                )
            }
        }
    }
}
