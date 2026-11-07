package com.kieran.optiframe.protocol

/** Applies frozen PC-007 controls around a composed P1 data canvas. */
object P1PhysicalFrameRenderer {
    private const val BLACK = 0
    private const val WHITE = 7

    fun render(frame: P1LogicalOpticalFrame): P1RenderedLogicalFrame {
        val width = P1PhysicalLayout.GRID_WIDTH
        val height = P1PhysicalLayout.GRID_HEIGHT
        val labels = IntArray(width * height) { P1FrameCanvas.EMPTY_CELL }

        // Reserved control cells have a defined dark default; the two timing
        // rows and the data region are populated below.
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!P1PhysicalLayout.isDataRegionCell(x, y)) {
                    labels[y * width + x] = BLACK
                } else {
                    labels[y * width + x] = frame.canvas.symbolAt(x, y)
                        ?: P1FrameCanvas.EMPTY_CELL
                }
            }
        }

        val header = OptiFrameGlobalHeaderMatrixCodec.encode(frame.header)
        drawHeader(labels, P1ControlBandLayout.leftHeader, header)
        drawHeader(labels, P1ControlBandLayout.rightHeader, header)

        P1ControlBandLayout.anchors.forEach { anchor ->
            for (localY in 0 until anchor.height) {
                for (localX in 0 until anchor.width) {
                    put(labels, anchor.x + localX, anchor.y + localY,
                        P1ControlBandLayout.anchorLabel(localX, localY))
                }
            }
        }

        drawPilots(labels, P1ControlBandLayout.leftPilots)
        drawPilots(labels, P1ControlBandLayout.rightPilots)

        val timing = P1TimingRecordCodec.encodeRepeatedRow(frame.header.frameSequence)
        for (column in timing.indices) {
            val label = if (timing[column]) WHITE else BLACK
            put(labels, P1ControlBandLayout.topTiming.x + column,
                P1ControlBandLayout.topTiming.y, label)
            put(labels, P1ControlBandLayout.bottomTiming.x + column,
                P1ControlBandLayout.bottomTiming.y, label)
        }

        return P1RenderedLogicalFrame(labels)
    }

    private fun drawHeader(
        labels: IntArray,
        rect: P1ControlBandLayout.Rect,
        matrix: GlobalHeaderLogicalMatrix
    ) {
        for (y in 0 until rect.height) {
            for (x in 0 until rect.width) {
                put(labels, rect.x + x, rect.y + y,
                    P1ControlBandLayout.headerLabel(matrix, x, y))
            }
        }
    }

    private fun drawPilots(labels: IntArray, rect: P1ControlBandLayout.Rect) {
        for (y in 0 until rect.height) {
            for (x in 0 until rect.width) {
                put(labels, rect.x + x, rect.y + y,
                    P1ControlBandLayout.pilotLabel(x, y))
            }
        }
    }

    private fun put(labels: IntArray, x: Int, y: Int, label: Int) {
        labels[y * P1PhysicalLayout.GRID_WIDTH + x] = label
    }
}
