package com.kieran.optiframe.acceptance

import java.util.Locale

data class P1AcceptanceRecord(
    val fast60: Boolean,
    val fileSizeBytes: Long,
    val sha256Verified: Boolean,
    val goodputKibPerSecond: Double?,
    val macroblockPassRate: Double?,
    val elapsedMillis: Long?,
    val firstMinuteKibPerSecond: Double?,
    val lastMinuteKibPerSecond: Double?
)

enum class P1AcceptancePreset(
    val label: String,
    val fileBytes: Long,
    val streakTarget: Int,
    val thermalMillis: Long
) {
    DEMO("Demo · 5 MiB ×3", 5L * 1024 * 1024, 3, 120_000),
    FULL("Full V1 · 100 MiB ×20", 100L * 1024 * 1024, 20, 600_000)
}

object P1AcceptanceChecklist {
    data class Item(val label: String, val value: String, val passed: Boolean?)

    fun evaluate(
        records: List<P1AcceptanceRecord>,
        preset: P1AcceptancePreset = P1AcceptancePreset.DEMO
    ): List<Item> {
        val qualifying = records.filter { it.fileSizeBytes >= preset.fileBytes }
        val qualifyingStreak = qualifying.asReversed()
            .takeWhile { it.sha256Verified }
            .size
        val streak = if (qualifying.isEmpty()) null else qualifyingStreak

        val safe30Verified = qualifying.filter { !it.fast60 && it.sha256Verified }
        val safe30Goodputs = safe30Verified.mapNotNull {
            it.goodputKibPerSecond?.takeIf(Double::isFinite)
        }
        val safe30PassRates = safe30Verified.mapNotNull {
            it.macroblockPassRate?.takeIf(Double::isFinite)
        }
        val safe30MedianGoodput = median(safe30Goodputs)
        val safe30MedianPassRate = median(safe30PassRates)

        val qualifyingFast60 = qualifying.filter { it.fast60 }
        val fast60Failures = qualifyingFast60.count { !it.sha256Verified }
        val fast60Goodputs = qualifyingFast60
            .filter { it.sha256Verified }
            .mapNotNull { it.goodputKibPerSecond?.takeIf(Double::isFinite) }
        val fast60MedianGoodput = median(fast60Goodputs)

        val thermalResults = qualifying.asSequence()
            .filter { it.sha256Verified && (it.elapsedMillis ?: 0L) >= preset.thermalMillis }
            .mapNotNull { record ->
                val first = record.firstMinuteKibPerSecond?.takeIf(Double::isFinite)
                val last = record.lastMinuteKibPerSecond?.takeIf(Double::isFinite)
                if (first == null || last == null) return@mapNotNull null
                val drop = if (first > 0.0) (first - last) / first * 100.0
                else if (last >= first * 0.75) 0.0 else 100.0
                drop to (last >= first * 0.75)
            }
            .toList()
        val bestThermalDrop = thermalResults.minOfOrNull { it.first }

        val thermalMinutes = preset.thermalMillis / 60_000
        val thermalLabel =
            "Thermal ($thermalMinutes-min run): goodput drop ≤25% (memory growth not measured)"

        return listOf(
            Item(
                label = "${preset.fileBytes / (1024 * 1024)} MiB SHA-256 streak",
                value = streak?.let { "$it/${preset.streakTarget}" } ?: "–",
                passed = streak?.let { it >= preset.streakTarget }
            ),
            Item(
                label = "SAFE30 median goodput ≥60 KiB/s",
                value = safe30MedianGoodput?.let {
                    String.format(Locale.US, "%.1f KiB/s (n=%d)", it, safe30Goodputs.size)
                } ?: "–",
                passed = safe30MedianGoodput?.let { it >= 60.0 }
            ),
            Item(
                label = "SAFE30 macroblock pass rate ≥90%",
                value = safe30MedianPassRate?.let {
                    String.format(Locale.US, "%.1f%%", it * 100.0)
                } ?: "–",
                passed = safe30MedianPassRate?.let { it >= 0.90 }
            ),
            Item(
                label = "FAST60 median goodput ≥115 KiB/s, no file errors",
                value = fast60MedianGoodput?.let {
                    val medianValue = String.format(
                        Locale.US,
                        "%.1f KiB/s (n=%d)",
                        it,
                        fast60Goodputs.size
                    )
                    if (fast60Failures == 0) medianValue
                    else "$medianValue · failures=$fast60Failures"
                } ?: if (fast60Failures > 0) "– · failures=$fast60Failures" else "–",
                passed = when {
                    fast60MedianGoodput == null && fast60Failures == 0 -> null
                    fast60MedianGoodput == null -> false
                    else -> fast60MedianGoodput >= 115.0 && fast60Failures == 0
                }
            ),
            Item(
                label = thermalLabel,
                value = bestThermalDrop?.let {
                    String.format(Locale.US, "best drop %.0f%%", it)
                } ?: "–",
                passed = if (bestThermalDrop == null) null else thermalResults.any { it.second }
            )
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
