package com.kieran.optiframe.acceptance

class P1GoodputWindow(
    private val sourceSymbolBytes: Int = 256
) {
    private data class Sample(val nowNanos: Long, val uniqueSymbols: Long)

    private val samples = mutableListOf<Sample>()
    private var startSample: Sample? = null

    var firstMinuteKibPerSecond: Double? = null
        private set

    var lastMinuteKibPerSecond: Double? = null
        private set

    init {
        require(sourceSymbolBytes > 0)
    }

    fun onProgress(nowNanos: Long, uniqueSymbols: Long) {
        val sample = Sample(nowNanos, uniqueSymbols)
        if (startSample == null) {
            startSample = sample
            samples += sample
            return
        }
        val previous = samples.last()
        if (nowNanos - previous.nowNanos < MIN_SAMPLE_INTERVAL_NANOS) return

        samples += sample
        val start = checkNotNull(startSample)
        if (sample.nowNanos - start.nowNanos >= MINUTE_NANOS) {
            if (firstMinuteKibPerSecond == null) {
                val firstMinuteEnd = start.nowNanos + MINUTE_NANOS
                val symbolsGained = (symbolCountAt(firstMinuteEnd) - start.uniqueSymbols)
                    .coerceAtLeast(0.0)
                firstMinuteKibPerSecond = toKibPerSecond(symbolsGained)
            }
            val lastMinuteStart = sample.nowNanos - MINUTE_NANOS
            val symbolsGained = (sample.uniqueSymbols - symbolCountAt(lastMinuteStart))
                .coerceAtLeast(0.0)
            lastMinuteKibPerSecond = toKibPerSecond(symbolsGained)
        }

        val trimBefore = sample.nowNanos - MAX_SAMPLE_AGE_NANOS
        while (samples.size > 1 && samples.first().nowNanos < trimBefore) {
            samples.removeAt(0)
        }
    }

    private fun symbolCountAt(timeNanos: Long): Double {
        val before = samples.lastOrNull { it.nowNanos <= timeNanos }
        val after = samples.firstOrNull { it.nowNanos >= timeNanos }
        if (before == null) return samples.first().uniqueSymbols.toDouble()
        if (after == null) return samples.last().uniqueSymbols.toDouble()
        if (before.nowNanos == after.nowNanos) return before.uniqueSymbols.toDouble()

        val fraction = (timeNanos - before.nowNanos).toDouble() /
            (after.nowNanos - before.nowNanos).toDouble()
        return before.uniqueSymbols +
            (after.uniqueSymbols - before.uniqueSymbols) * fraction
    }

    private fun toKibPerSecond(symbols: Double): Double =
        symbols * sourceSymbolBytes / 1024.0 / 60.0

    companion object {
        private const val MIN_SAMPLE_INTERVAL_NANOS = 1_000_000_000L
        private const val MINUTE_NANOS = 60_000_000_000L
        private const val MAX_SAMPLE_AGE_NANOS = 61_000_000_000L
    }
}
