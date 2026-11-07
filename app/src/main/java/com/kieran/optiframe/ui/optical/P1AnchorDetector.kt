package com.kieran.optiframe.ui.optical

import com.kieran.optiframe.protocol.P1ControlBandLayout
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Detects and refines the four identical P1 corner anchors in a luma plane. */
object P1AnchorDetector {
    const val MAX_P95_ERROR_CELLS = 0.35
    private const val MAX_ROLL_DEGREES = 15
    private const val PREFILTER_MIN_MATCHES = 28
    private const val MAX_QUAD_CLUSTERS = 12

    data class Detection(
        val anchors: P1PerspectiveSampler.AnchorPoints,
        val homography: DoubleArray,
        val inliers: Int,
        val correspondences: Int,
        val rmsErrorCells: Double,
        val p95ErrorCells: Double
    )

    private data class Pattern(val center: Double, val module: Double)
    private data class Candidate(val x: Double, val y: Double, val module: Double)
    private data class Cluster(
        var x: Double, var y: Double, var module: Double, var hits: Int, val score: Int = 0
    )
    private data class Correspondence(
        val canonical: Pair<Double, Double>,
        val image: Pair<Double, Double>
    )
    private data class ScoredDetection(val detection: Detection)
    private data class NormalizedPoints(
        val points: List<Pair<Double, Double>>,
        val centerX: Double,
        val centerY: Double,
        val scale: Double
    )

    private val anchorRects = listOf(
        P1ControlBandLayout.anchors[0],
        P1ControlBandLayout.anchors[1],
        P1ControlBandLayout.anchors[3],
        P1ControlBandLayout.anchors[2]
    )
    private val anchors = anchorRects.map { rect ->
        (rect.x + rect.width / 2.0) to (rect.y + rect.height / 2.0)
    }
    private val edgeOffsets = doubleArrayOf(1.0, 2.0, 4.0, 5.0)

    /**
     * The identical anchor pattern has a 180-degree orientation ambiguity; ordering
     * assumes the frame is roughly upright (less than 45 degrees of roll).
     */
    internal fun detect(luma: P1PerspectiveSampler.PlaneView): Detection? {
        if (luma.width <= 0 || luma.height <= 0 || luma.rowStride <= 0 || luma.pixelStride <= 0) {
            return null
        }
        val lastOffset = (luma.height - 1L) * luma.rowStride +
            (luma.width - 1L) * luma.pixelStride
        if (lastOffset < 0 || lastOffset >= luma.data.size) return null

        val threshold = otsuThreshold(luma)
        val dark = BooleanArray(luma.width * luma.height)
        for (y in 0 until luma.height) {
            var offset = y * luma.rowStride
            val rowOffset = y * luma.width
            for (x in 0 until luma.width) {
                dark[rowOffset + x] = (luma.data[offset].toInt() and 0xFF) <= threshold
                offset += luma.pixelStride
            }
        }
        val candidates = findCandidates(dark, luma.width, luma.height)
        val rawClusters = clusterCandidates(candidates)
        val potentialClusters = rawClusters.filter { it.hits >= 2 }
        val refined = potentialClusters
            .mapNotNull { refineAnchor(dark, luma.width, luma.height, it) }
            .sortedWith(compareByDescending<Cluster> { it.score }.thenByDescending { it.hits })
        // Rank by template agreement, not row hits: dense random data produces many
        // high-hit false patterns, while true anchors match the 6x6 template almost perfectly.
        val clusters = ArrayList<Cluster>()
        for (cluster in refined) {
            if (clusters.none { hypot(it.x - cluster.x, it.y - cluster.y) < 2.0 * max(it.module, cluster.module) }) {
                clusters += cluster
                if (clusters.size == MAX_QUAD_CLUSTERS) break
            }
        }
        if (clusters.size < 4) return null

        var best: ScoredDetection? = null
        for (a in 0 until clusters.size - 3) {
            for (b in a + 1 until clusters.size - 2) {
                for (c in b + 1 until clusters.size - 1) {
                    for (d in c + 1 until clusters.size) {
                        val quad = orderQuad(listOf(clusters[a], clusters[b], clusters[c], clusters[d]))
                            ?: continue
                        if (!validQuad(quad)) continue
                        val evaluated = runCatching {
                            refineDetection(luma, threshold, quad)
                        }.getOrNull() ?: continue
                        val previous = best?.detection
                        if (previous == null || evaluated.inliers > previous.inliers ||
                            (evaluated.inliers == previous.inliers &&
                                evaluated.rmsErrorCells < previous.rmsErrorCells)
                        ) {
                            best = ScoredDetection(evaluated)
                        }
                    }
                }
            }
        }
        return best?.detection?.takeIf {
            it.inliers >= 28 && it.p95ErrorCells <= MAX_P95_ERROR_CELLS
        }
    }

    /** Fits canonical-to-image H by normalized least squares; the returned H has h[8] = 1. */
    internal fun fitHomography(
        src: List<Pair<Double, Double>>,
        dst: List<Pair<Double, Double>>
    ): DoubleArray {
        require(src.size == dst.size && src.size >= 4) { "At least four point pairs are required" }
        require(src.all { it.first.isFinite() && it.second.isFinite() } &&
            dst.all { it.first.isFinite() && it.second.isFinite() }
        ) { "Homography points must be finite" }

        val normalizedSrc = normalize(src)
        val normalizedDst = normalize(dst)
        val normal = Array(8) { DoubleArray(8) }
        val right = DoubleArray(8)
        for (index in src.indices) {
            val (u, v) = normalizedSrc.points[index]
            val (x, y) = normalizedDst.points[index]
            accumulate(normal, right, doubleArrayOf(u, v, 1.0, 0.0, 0.0, 0.0, -x * u, -x * v), x)
            accumulate(normal, right, doubleArrayOf(0.0, 0.0, 0.0, u, v, 1.0, -y * u, -y * v), y)
        }
        val coefficients = solve(normal, right)
        val normalizedH = arrayOf(
            doubleArrayOf(coefficients[0], coefficients[1], coefficients[2]),
            doubleArrayOf(coefficients[3], coefficients[4], coefficients[5]),
            doubleArrayOf(coefficients[6], coefficients[7], 1.0)
        )
        val sourceTransform = arrayOf(
            doubleArrayOf(normalizedSrc.scale, 0.0, -normalizedSrc.scale * normalizedSrc.centerX),
            doubleArrayOf(0.0, normalizedSrc.scale, -normalizedSrc.scale * normalizedSrc.centerY),
            doubleArrayOf(0.0, 0.0, 1.0)
        )
        val inverseDestinationTransform = arrayOf(
            doubleArrayOf(1.0 / normalizedDst.scale, 0.0, normalizedDst.centerX),
            doubleArrayOf(0.0, 1.0 / normalizedDst.scale, normalizedDst.centerY),
            doubleArrayOf(0.0, 0.0, 1.0)
        )
        val homography = multiply(
            multiply(inverseDestinationTransform, normalizedH),
            sourceTransform
        )
        val scale = homography[2][2]
        require(abs(scale) > 1e-12 && scale.isFinite()) { "Homography cannot be normalized" }
        return doubleArrayOf(
            homography[0][0] / scale, homography[0][1] / scale, homography[0][2] / scale,
            homography[1][0] / scale, homography[1][1] / scale, homography[1][2] / scale,
            homography[2][0] / scale, homography[2][1] / scale
        )
    }

    private fun findCandidates(dark: BooleanArray, width: Int, height: Int): List<Candidate> {
        val rowStep = if (height > 720) 2 else 1
        val verticalPatterns = HashMap<Int, List<Pattern>>()
        val candidates = ArrayList<Candidate>()
        for (y in 0 until height step rowStep) {
            for (horizontal in findPatterns(dark, y * width, 1, width)) {
                val radius = ceil(horizontal.module * 1.5).toInt()
                val minX = (horizontal.center.roundToInt() - radius).coerceAtLeast(0)
                val maxX = (horizontal.center.roundToInt() + radius).coerceAtMost(width - 1)
                var bestVertical: Pattern? = null
                var bestScore = Double.POSITIVE_INFINITY
                for (column in minX..maxX) {
                    val matches = verticalPatterns.getOrPut(column) {
                        findPatterns(dark, column, width, height)
                    }
                    for (vertical in matches) {
                        val verticalDistance = abs(vertical.center - y)
                        if (verticalDistance > 1.25 * max(horizontal.module, vertical.module)) continue
                        val score = verticalDistance + abs(column - horizontal.center)
                        if (score < bestScore) {
                            bestVertical = vertical
                            bestScore = score
                        }
                    }
                }
                val vertical = bestVertical ?: continue
                candidates += Candidate(
                    horizontal.center,
                    vertical.center,
                    (horizontal.module + vertical.module) / 2.0
                )
            }
        }
        return candidates
    }

    private fun findPatterns(
        dark: BooleanArray,
        startOffset: Int,
        stride: Int,
        length: Int
    ): List<Pattern> {
        if (length < 5) return emptyList()
        val starts = IntArray(length)
        val lengths = IntArray(length)
        val colors = BooleanArray(length)
        var runCount = 0
        var runStart = 0
        var color = dark[startOffset]
        for (position in 1..length) {
            val nextColor = if (position == length) !color else dark[startOffset + position * stride]
            if (position == length || nextColor != color) {
                starts[runCount] = runStart
                lengths[runCount] = position - runStart
                colors[runCount] = color
                runCount++
                runStart = position
                color = nextColor
            }
        }

        val matches = ArrayList<Pattern>()
        for (index in 0..runCount - 5) {
            if (!colors[index] || colors[index + 1] || !colors[index + 2] ||
                colors[index + 3] || !colors[index + 4]
            ) continue
            val module =
                (lengths[index + 1] + lengths[index + 3] + lengths[index + 2] / 2.0) / 3.0
            if (module <= 0.0) continue
            if (lengths[index] < 0.5 * module ||
                lengths[index + 1] < 0.5 * module || lengths[index + 1] > 1.5 * module ||
                lengths[index + 3] < 0.5 * module || lengths[index + 3] > 1.5 * module ||
                lengths[index + 4] < 0.5 * module ||
                lengths[index + 2] < 1.3 * module || lengths[index + 2] > 2.7 * module
            ) continue
            val center = starts[index + 2] + lengths[index + 2] / 2.0
            matches += Pattern(center, module)
        }
        return matches
    }

    private fun clusterCandidates(candidates: List<Candidate>): List<Cluster> {
        if (candidates.isEmpty()) return emptyList()
        val moduleSizes = candidates.map { it.module }.sorted()
        val bucketSize = max(1.0, moduleSizes[moduleSizes.size / 2] * 2.0)
        val buckets = HashMap<Long, MutableList<Cluster>>()
        val clusters = ArrayList<Cluster>()

        for (candidate in candidates) {
            val bucketX = floor(candidate.x / bucketSize).toInt()
            val bucketY = floor(candidate.y / bucketSize).toInt()
            var closest: Cluster? = null
            var closestDistance = Double.POSITIVE_INFINITY
            for (dx in -3..3) for (dy in -3..3) {
                for (cluster in buckets[bucketKey(bucketX + dx, bucketY + dy)].orEmpty()) {
                    val distance = hypot(cluster.x - candidate.x, cluster.y - candidate.y)
                    if (distance <= 2.0 * max(cluster.module, candidate.module) &&
                        distance < closestDistance
                    ) {
                        closest = cluster
                        closestDistance = distance
                    }
                }
            }
            if (closest == null) {
                val cluster = Cluster(candidate.x, candidate.y, candidate.module, 1)
                clusters += cluster
                buckets.getOrPut(bucketKey(bucketX, bucketY)) { ArrayList() } += cluster
            } else {
                val previousHits = closest.hits
                val updatedHits = previousHits + 1
                closest.x = (closest.x * previousHits + candidate.x) / updatedHits
                closest.y = (closest.y * previousHits + candidate.y) / updatedHits
                closest.module = (closest.module * previousHits + candidate.module) / updatedHits
                closest.hits = updatedHits
            }
        }
        return clusters
    }

    private fun refineAnchor(dark: BooleanArray, width: Int, height: Int, cluster: Cluster): Cluster? {
        val offsetStep = max(1.0, cluster.module / 2.0)
        if (coarseTemplateMatches(dark, width, height, cluster, offsetStep) < PREFILTER_MIN_MATCHES) return null
        var bestMatches = 0
        var bestX = cluster.x
        var bestY = cluster.y
        var bestModule = cluster.module
        for (moduleFactor in doubleArrayOf(0.8, 0.9, 1.0, 1.1, 1.2)) {
            val module = cluster.module * moduleFactor
            for (angleDegrees in -MAX_ROLL_DEGREES..MAX_ROLL_DEGREES step 3) {
                val angle = Math.toRadians(angleDegrees.toDouble())
                val cosine = kotlin.math.cos(angle)
                val sine = kotlin.math.sin(angle)
                for (offsetX in -3..3) for (offsetY in -3..3) {
                    val x = cluster.x + offsetX * offsetStep
                    val y = cluster.y + offsetY * offsetStep
                    var matches = 0
                    for (localY in 0 until 6) for (localX in 0 until 6) {
                        val dx = (localX - 2.5) * module
                        val dy = (localY - 2.5) * module
                        val sampleX = (x + cosine * dx - sine * dy).roundToInt()
                        val sampleY = (y + sine * dx + cosine * dy).roundToInt()
                        if (sampleX !in 0 until width || sampleY !in 0 until height) continue
                        val expectedDark = P1ControlBandLayout.anchorLabel(localX, localY) == 0
                        if (dark[sampleY * width + sampleX] == expectedDark) matches++
                    }
                    if (matches > bestMatches) {
                        bestMatches = matches
                        bestX = x
                        bestY = y
                        bestModule = module
                    }
                }
            }
        }
        return if (bestMatches >= 32) Cluster(bestX, bestY, bestModule, cluster.hits, bestMatches) else null
    }

    /** Cheap template check that rejects most false row patterns before the full search. */
    private fun coarseTemplateMatches(
        dark: BooleanArray, width: Int, height: Int, cluster: Cluster, offsetStep: Double
    ): Int {
        var best = 0
        for (moduleFactor in doubleArrayOf(0.75, 1.0, 1.25)) for (angleDegrees in -12..12 step 6) {
            val module = cluster.module * moduleFactor
            val angle = Math.toRadians(angleDegrees.toDouble())
            val cosine = kotlin.math.cos(angle)
            val sine = kotlin.math.sin(angle)
            for (offsetX in -2..2) for (offsetY in -2..2) {
                val x = cluster.x + offsetX * offsetStep
                val y = cluster.y + offsetY * offsetStep
                var matches = 0
                for (localY in 0 until 6) for (localX in 0 until 6) {
                    val dx = (localX - 2.5) * module
                    val dy = (localY - 2.5) * module
                    val sampleX = (x + cosine * dx - sine * dy).roundToInt()
                    val sampleY = (y + sine * dx + cosine * dy).roundToInt()
                    if (sampleX !in 0 until width || sampleY !in 0 until height) continue
                    val expectedDark = P1ControlBandLayout.anchorLabel(localX, localY) == 0
                    if (dark[sampleY * width + sampleX] == expectedDark) matches++
                }
                if (matches > best) best = matches
            }
        }
        return best
    }

    private fun bucketKey(x: Int, y: Int): Long =
        (x.toLong() shl 32) xor (y.toLong() and 0xFFFF_FFFFL)

    private fun orderQuad(points: List<Cluster>): List<Cluster>? {
        val centerX = points.sumOf { it.x } / 4.0
        val centerY = points.sumOf { it.y } / 4.0
        val around = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val topLeft = around.indices.minByOrNull { around[it].x + around[it].y } ?: return null
        return List(4) { around[(topLeft + it) % 4] }
    }

    private fun validQuad(quad: List<Cluster>): Boolean {
        val modules = quad.map { it.module }
        if (modules.minOrNull() == null || modules.minOrNull()!! <= 0.0 ||
            modules.maxOrNull()!! / modules.minOrNull()!! > 2.0
        ) return false
        val turns = DoubleArray(4) { index ->
            val a = quad[index]
            val b = quad[(index + 1) % 4]
            val c = quad[(index + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return turns.all { it > 1e-6 } || turns.all { it < -1e-6 }
    }

    private fun refineDetection(
        luma: P1PerspectiveSampler.PlaneView,
        threshold: Int,
        quad: List<Cluster>
    ): Detection {
        val canonicalCenters = anchors
        var homography = fitHomography(canonicalCenters, quad.map { it.x to it.y })
        val correspondences = ArrayList<Correspondence>(36)
        for (index in quad.indices) {
            val rect = anchorRects[index]
            val (centerX, centerY) = canonicalCenters[index]
            val candidate = quad[index]
            correspondences += Correspondence(centerX to centerY, candidate.x to candidate.y)
            for (offset in edgeOffsets) {
                val expectedX = rect.x + offset
                val observedX = findTransition(
                    luma, threshold, homography, expectedX, centerY, horizontal = true
                ) ?: error("Missing horizontal anchor edge")
                correspondences += Correspondence(
                    expectedX to centerY,
                    P1PerspectiveSampler.map(homography, observedX, centerY)
                )

                val expectedY = rect.y + offset
                val observedY = findTransition(
                    luma, threshold, homography, expectedY, centerX, horizontal = false
                ) ?: error("Missing vertical anchor edge")
                correspondences += Correspondence(
                    centerX to expectedY,
                    P1PerspectiveSampler.map(homography, centerX, observedY)
                )
            }
        }

        homography = fitHomography(
            correspondences.map { it.canonical },
            correspondences.map { it.image }
        )
        var errors = correspondenceErrors(homography, correspondences)
        val inlierIndices = errors.indices.filter { errors[it] < 0.5 }
        require(inlierIndices.size >= 4) { "Insufficient homography inliers" }
        homography = fitHomography(
            inlierIndices.map { correspondences[it].canonical },
            inlierIndices.map { correspondences[it].image }
        )
        errors = correspondenceErrors(homography, correspondences)
        val finalInliers = errors.filter { it < 0.5 }
        val rms = sqrt(finalInliers.sumOf { it * it } / finalInliers.size)
        val sortedErrors = errors.sorted()
        val p95 = sortedErrors[(ceil(sortedErrors.size * 0.95).toInt() - 1).coerceAtLeast(0)]
        val points = canonicalCenters.map { P1PerspectiveSampler.map(homography, it.first, it.second) }
        return Detection(
            anchors = P1PerspectiveSampler.AnchorPoints(
                points[0].first, points[0].second,
                points[1].first, points[1].second,
                points[2].first, points[2].second,
                points[3].first, points[3].second
            ),
            homography = homography,
            inliers = finalInliers.size,
            correspondences = correspondences.size,
            rmsErrorCells = rms,
            p95ErrorCells = p95
        )
    }

    private fun findTransition(
        luma: P1PerspectiveSampler.PlaneView,
        threshold: Int,
        homography: DoubleArray,
        expected: Double,
        fixed: Double,
        horizontal: Boolean
    ): Double? {
        val start = expected - 0.75
        val end = expected + 0.75
        val steps = ceil((end - start) / 0.05).toInt()
        var previousPosition = start
        var previousValue = sampleCanonical(luma, homography, previousPosition, fixed, horizontal)
            ?: return null
        var nearest: Double? = null
        var nearestDistance = Double.POSITIVE_INFINITY
        for (step in 1..steps) {
            val position = start + (end - start) * step / steps
            val value = sampleCanonical(luma, homography, position, fixed, horizontal) ?: return null
            if ((previousValue <= threshold) != (value <= threshold)) {
                val fraction = ((threshold - previousValue) / (value - previousValue)).coerceIn(0.0, 1.0)
                val crossing = previousPosition + (position - previousPosition) * fraction
                val distance = abs(crossing - expected)
                if (distance < nearestDistance) {
                    nearest = crossing
                    nearestDistance = distance
                }
            }
            previousPosition = position
            previousValue = value
        }
        return nearest
    }

    private fun sampleCanonical(
        luma: P1PerspectiveSampler.PlaneView,
        homography: DoubleArray,
        position: Double,
        fixed: Double,
        horizontal: Boolean
    ): Double? {
        val imagePoint = if (horizontal) {
            P1PerspectiveSampler.map(homography, position, fixed)
        } else {
            P1PerspectiveSampler.map(homography, fixed, position)
        }
        if (imagePoint.first !in 0.0..(luma.width - 1.0) ||
            imagePoint.second !in 0.0..(luma.height - 1.0)
        ) return null
        return P1PerspectiveSampler.bilinear(luma, imagePoint.first, imagePoint.second)
    }

    private fun correspondenceErrors(
        homography: DoubleArray,
        correspondences: List<Correspondence>
    ): List<Double> {
        val inverse = inverseHomography(homography)
        return correspondences.map { correspondence ->
            val projected = P1PerspectiveSampler.map(
                inverse, correspondence.image.first, correspondence.image.second
            )
            hypot(
                projected.first - correspondence.canonical.first,
                projected.second - correspondence.canonical.second
            )
        }
    }

    private fun inverseHomography(h: DoubleArray): DoubleArray {
        val a = h[0]; val b = h[1]; val c = h[2]
        val d = h[3]; val e = h[4]; val f = h[5]
        val g = h[6]; val i = h[7]
        val determinant = a * (e - f * i) - b * (d - f * g) + c * (d * i - e * g)
        require(abs(determinant) > 1e-12 && determinant.isFinite()) { "Degenerate homography" }
        val inverse = arrayOf(
            doubleArrayOf(e - f * i, c * i - b, b * f - c * e),
            doubleArrayOf(f * g - d, a - c * g, c * d - a * f),
            doubleArrayOf(d * i - e * g, b * g - a * i, a * e - b * d)
        )
        val scale = inverse[2][2] / determinant
        require(abs(scale) > 1e-12 && scale.isFinite()) { "Inverse homography cannot be normalized" }
        return doubleArrayOf(
            inverse[0][0] / inverse[2][2], inverse[0][1] / inverse[2][2], inverse[0][2] / inverse[2][2],
            inverse[1][0] / inverse[2][2], inverse[1][1] / inverse[2][2], inverse[1][2] / inverse[2][2],
            inverse[2][0] / inverse[2][2], inverse[2][1] / inverse[2][2]
        )
    }

    private fun otsuThreshold(luma: P1PerspectiveSampler.PlaneView): Int {
        val histogram = LongArray(256)
        var total = 0L
        var sum = 0L
        for (y in 0 until luma.height) {
            var offset = y * luma.rowStride
            for (x in 0 until luma.width) {
                val value = luma.data[offset].toInt() and 0xFF
                histogram[value]++
                total++
                sum += value
                offset += luma.pixelStride
            }
        }
        var backgroundWeight = 0L
        var backgroundSum = 0L
        var bestThreshold = 0
        var bestVariance = -1.0
        for (threshold in 0..255) {
            backgroundWeight += histogram[threshold]
            if (backgroundWeight == 0L) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0L) break
            backgroundSum += threshold * histogram[threshold]
            val backgroundMean = backgroundSum.toDouble() / backgroundWeight
            val foregroundMean = (sum - backgroundSum).toDouble() / foregroundWeight
            val difference = backgroundMean - foregroundMean
            val variance = backgroundWeight.toDouble() * foregroundWeight * difference * difference
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = threshold
            }
        }
        return bestThreshold
    }

    private fun normalize(points: List<Pair<Double, Double>>): NormalizedPoints {
        val centerX = points.sumOf { it.first } / points.size
        val centerY = points.sumOf { it.second } / points.size
        val meanDistance = points.sumOf { hypot(it.first - centerX, it.second - centerY) } / points.size
        require(meanDistance > 1e-12 && meanDistance.isFinite()) { "Degenerate homography points" }
        val scale = sqrt(2.0) / meanDistance
        return NormalizedPoints(
            points.map { (x, y) -> (scale * (x - centerX)) to (scale * (y - centerY)) },
            centerX, centerY, scale
        )
    }

    private fun accumulate(normal: Array<DoubleArray>, right: DoubleArray, row: DoubleArray, value: Double) {
        for (i in 0 until 8) {
            right[i] += row[i] * value
            for (j in 0 until 8) normal[i][j] += row[i] * row[j]
        }
    }

    private fun solve(matrix: Array<DoubleArray>, right: DoubleArray): DoubleArray {
        val augmented = Array(8) { row -> DoubleArray(9) { column ->
            if (column == 8) right[row] else matrix[row][column]
        } }
        for (column in 0 until 8) {
            var pivot = column
            for (row in column + 1 until 8) {
                if (abs(augmented[row][column]) > abs(augmented[pivot][column])) pivot = row
            }
            require(abs(augmented[pivot][column]) > 1e-12) { "Degenerate homography points" }
            val swap = augmented[column]
            augmented[column] = augmented[pivot]
            augmented[pivot] = swap
            val divisor = augmented[column][column]
            for (item in column..8) augmented[column][item] /= divisor
            for (row in 0 until 8) {
                if (row == column) continue
                val factor = augmented[row][column]
                for (item in column..8) augmented[row][item] -= factor * augmented[column][item]
            }
        }
        return DoubleArray(8) { augmented[it][8] }
    }

    private fun multiply(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        Array(3) { row ->
            DoubleArray(3) { column ->
                (0..2).sumOf { index -> left[row][index] * right[index][column] }
            }
        }
}
