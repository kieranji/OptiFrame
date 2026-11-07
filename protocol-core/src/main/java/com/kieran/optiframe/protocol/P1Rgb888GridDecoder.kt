package com.kieran.optiframe.protocol

/** Decodes a perspective-corrected, one-RGB-pixel-per-cell P1 grid. */
object P1Rgb888GridDecoder {
    private const val MINIMUM_PALETTE_SEPARATION = 8.0

    data class DecodedGrid(
        val frame: P1LogicalOpticalFrame,
        val cellConfidence: DoubleArray
    )

    /**
     * The input is row-major RGB888 for exactly 160×96 logical cells. Camera
     * geometry and area sampling happen upstream; the returned confidences can
     * be passed directly to [P1M3ReceiverSession.acceptVsyncFrame].
     */
    fun decode(
        rgb888: ByteArray,
        emptySlotConfidenceThreshold: Double = 0.9
    ): DecodedGrid {
        require(emptySlotConfidenceThreshold in 0.0..1.0)
        val width = P1PhysicalLayout.GRID_WIDTH
        val height = P1PhysicalLayout.GRID_HEIGHT
        require(rgb888.size == width * height * P1PhysicalFrameRasterizer.BYTES_PER_PIXEL) {
            "P1 RGB grid must be exactly ${width}×${height} RGB888"
        }

        val palette = calibratePalette(rgb888)
        val labels = IntArray(width * height)
        val confidence = DoubleArray(width * height)
        for (cellIndex in labels.indices) {
            val pixelOffset = cellIndex * P1PhysicalFrameRasterizer.BYTES_PER_PIXEL
            val sample = doubleArrayOf(
                (rgb888[pixelOffset].toInt() and 0xFF).toDouble(),
                (rgb888[pixelOffset + 1].toInt() and 0xFF).toDouble(),
                (rgb888[pixelOffset + 2].toInt() and 0xFF).toDouble()
            )
            val classified = classify(sample, palette)
            labels[cellIndex] = classified.first
            confidence[cellIndex] = classified.second
        }

        val header = decodeHeaderCopies(labels)
        val topTiming = decodeTimingRow(labels, P1ControlBandLayout.topTiming)
        val bottomTiming = decodeTimingRow(labels, P1ControlBandLayout.bottomTiming)
        if (topTiming != bottomTiming) {
            throw P1CompositeFrameException("PC-007 top and bottom timing rows disagree")
        }
        if (topTiming.frameSequenceLow16 != (header.frameSequence and 0xFFFFL).toInt()) {
            throw P1CompositeFrameException("PC-007 timing sequence does not match the Global Header")
        }

        val canvasCells = IntArray(width * height) { P1FrameCanvas.EMPTY_CELL }
        for (row in 0 until P1PhysicalLayout.DATA_REGION_HEIGHT) {
            val y = P1PhysicalLayout.DATA_REGION_Y + row
            for (column in 0 until P1PhysicalLayout.DATA_REGION_WIDTH) {
                val x = P1PhysicalLayout.DATA_REGION_X + column
                canvasCells[y * width + x] = labels[y * width + x]
            }
        }
        markUniformDarkUnusedSlots(canvasCells, labels, confidence, emptySlotConfidenceThreshold)

        return DecodedGrid(
            frame = P1LogicalOpticalFrame(header, P1FrameCanvas(canvasCells)),
            cellConfidence = confidence
        )
    }

    private fun decodeHeaderCopies(labels: IntArray): OptiFrameGlobalHeader {
        val left = decodeHeaderCopy(labels, P1ControlBandLayout.leftHeader)
        val right = decodeHeaderCopy(labels, P1ControlBandLayout.rightHeader)
        return when {
            left != null && right != null -> {
                if (left != right) throw P1CompositeFrameException("PC-007 Global Header copies disagree")
                left
            }
            left != null -> left
            right != null -> right
            else -> throw IllegalArgumentException("Neither PC-007 Global Header copy decoded")
        }
    }

    private fun decodeHeaderCopy(
        labels: IntArray,
        rect: P1ControlBandLayout.Rect
    ): OptiFrameGlobalHeader? = try {
        val cells = IntArray(256)
        val erasures = linkedSetOf<Int>()
        for (y in 0 until rect.height) {
            for (x in 0 until rect.width) {
                val bitIndex = y * rect.width + x
                val label = labels[(rect.y + y) * P1PhysicalLayout.GRID_WIDTH + rect.x + x]
                when (label) {
                    0 -> cells[bitIndex] = 0
                    7 -> cells[bitIndex] = 1
                    else -> {
                        cells[bitIndex] = 0
                        if (bitIndex < ProtocolConstants.GLOBAL_HEADER_RS_BITS) {
                            erasures += bitIndex / 8
                        }
                    }
                }
            }
        }
        OptiFrameGlobalHeaderMatrixCodec.decode(
            GlobalHeaderLogicalMatrix(cells), erasures.toIntArray()
        )
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private fun decodeTimingRow(
        labels: IntArray,
        rect: P1ControlBandLayout.Rect
    ): P1TimingRecordCodec.TimingRecord {
        val bits = BooleanArray(rect.width) { column ->
            when (val label = labels[rect.y * P1PhysicalLayout.GRID_WIDTH + rect.x + column]) {
                0 -> false
                7 -> true
                else -> throw IllegalArgumentException("Invalid binary PC-007 timing label $label")
            }
        }
        return try {
            P1TimingRecordCodec.decodeRepeatedRow(bits)
        } catch (error: IllegalArgumentException) {
            if (error.message == "PC-007 repeated timing records disagree") {
                throw P1CompositeFrameException(error.message ?: "PC-007 timing copies disagree")
            }
            throw error
        }
    }

    /** A uniformly high-confidence dark macroblock is the renderer's DATA_UNUSED fill. */
    private fun markUniformDarkUnusedSlots(
        canvasCells: IntArray,
        labels: IntArray,
        confidence: DoubleArray,
        threshold: Double
    ) {
        for (position in P1SlotPosition.canonicalP1()) {
            val originX = P1PhysicalLayout.DATA_REGION_X +
                position.macroblockColumn * P1PhysicalLayout.MACROBLOCK_WIDTH
            val originY = P1PhysicalLayout.DATA_REGION_Y +
                position.macroblockRow * P1PhysicalLayout.MACROBLOCK_HEIGHT
            var uniformDark = true
            for (row in 0 until P1PhysicalLayout.MACROBLOCK_HEIGHT) {
                for (column in 0 until P1PhysicalLayout.MACROBLOCK_WIDTH) {
                    val index = (originY + row) * P1PhysicalLayout.GRID_WIDTH + originX + column
                    if (labels[index] != 0 || confidence[index] < threshold) uniformDark = false
                }
            }
            if (uniformDark) {
                for (row in 0 until P1PhysicalLayout.MACROBLOCK_HEIGHT) {
                    for (column in 0 until P1PhysicalLayout.MACROBLOCK_WIDTH) {
                        canvasCells[(originY + row) * P1PhysicalLayout.GRID_WIDTH + originX + column] =
                            P1FrameCanvas.EMPTY_CELL
                    }
                }
            }
        }
    }

    /** Estimate all eight observed colors from the duplicated pilot patches. */
    private fun calibratePalette(rgb888: ByteArray): Array<DoubleArray> {
        val sums = Array(8) { DoubleArray(3) }
        val counts = IntArray(8)
        for (rect in listOf(P1ControlBandLayout.leftPilots, P1ControlBandLayout.rightPilots)) {
            for (localY in 0 until rect.height) {
                for (localX in 0 until rect.width) {
                    val label = P1ControlBandLayout.pilotLabel(localX, localY)
                    val offset = ((rect.y + localY) * P1PhysicalLayout.GRID_WIDTH + rect.x + localX) * 3
                    for (channel in 0..2) {
                        sums[label][channel] += rgb888[offset + channel].toInt() and 0xFF
                    }
                    counts[label]++
                }
            }
        }
        val palette = Array(8) { label ->
            require(counts[label] > 0) { "Missing OF8 pilot color $label" }
            DoubleArray(3) { channel -> sums[label][channel] / counts[label] }
        }
        var nearestSeparationSquared = Double.POSITIVE_INFINITY
        for (first in palette.indices) {
            for (second in first + 1 until palette.size) {
                nearestSeparationSquared = minOf(
                    nearestSeparationSquared,
                    distanceSquared(palette[first], palette[second])
                )
            }
        }
        require(nearestSeparationSquared >= MINIMUM_PALETTE_SEPARATION * MINIMUM_PALETTE_SEPARATION) {
            "OF8 pilot colors are too close to classify; adjust focus, exposure, or screen brightness"
        }
        return palette
    }

    private fun classify(sample: DoubleArray, palette: Array<DoubleArray>): Pair<Int, Double> {
        var bestLabel = -1
        var bestDistance = Double.POSITIVE_INFINITY
        var secondDistance = Double.POSITIVE_INFINITY
        for (label in palette.indices) {
            val distance = distanceSquared(sample, palette[label])
            if (distance < bestDistance) {
                secondDistance = bestDistance
                bestDistance = distance
                bestLabel = label
            } else if (distance < secondDistance) {
                secondDistance = distance
            }
        }
        val marginConfidence = if (bestDistance == 0.0) 1.0 else {
            ((secondDistance - bestDistance) / (secondDistance + 1.0)).coerceIn(0.0, 1.0)
        }
        val confidence = marginConfidence *
            (1.0 - kotlin.math.sqrt(bestDistance) / (MINIMUM_PALETTE_SEPARATION * 6.0))
                .coerceIn(0.0, 1.0)
        return bestLabel to confidence
    }

    private fun distanceSquared(first: DoubleArray, second: DoubleArray): Double {
        var result = 0.0
        for (channel in 0..2) {
            val difference = first[channel] - second[channel]
            result += difference * difference
        }
        return result
    }
}
