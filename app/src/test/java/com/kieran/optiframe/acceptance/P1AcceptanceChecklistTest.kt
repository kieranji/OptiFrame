package com.kieran.optiframe.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P1AcceptanceChecklistTest {
    private val preset = P1AcceptancePreset.DEMO

    @Test
    fun emptyRecordsHaveNoChecklistData() {
        val items = P1AcceptanceChecklist.evaluate(emptyList(), preset)

        assertEquals(5, items.size)
        assertTrue(items.all { it.passed == null })
    }

    @Test
    fun qualifyingFailureResetsTheTrailingVerifiedStreak() {
        val verified = record()
        val failed = record(sha256Verified = false)

        assertEquals("2/3", P1AcceptanceChecklist.evaluate(listOf(verified, verified), preset)[0].value)
        assertEquals("0/3", P1AcceptanceChecklist.evaluate(listOf(verified, verified, failed), preset)[0].value)
        assertEquals("1/3", P1AcceptanceChecklist.evaluate(listOf(verified, verified, failed, verified), preset)[0].value)
    }

    @Test
    fun smallFilesAreIgnored() {
        val small = record(
            fileSizeBytes = preset.fileBytes - 1,
            goodputKibPerSecond = 1_000.0,
            macroblockPassRate = 1.0
        )
        val noQualifyingData = P1AcceptanceChecklist.evaluate(listOf(small), preset)

        assertTrue(noQualifyingData.all { it.passed == null })

        val qualifying = record(goodputKibPerSecond = 60.0, macroblockPassRate = 0.90)
        val withQualifyingData = P1AcceptanceChecklist.evaluate(listOf(small, qualifying), preset)
        assertEquals("60.0 KiB/s (n=1)", withQualifyingData[1].value)
        assertEquals("90.0%", withQualifyingData[2].value)
    }

    @Test
    fun mediansHandleOddAndEvenSampleCounts() {
        val oddRecords = listOf(
            record(goodputKibPerSecond = 10.0, macroblockPassRate = 0.50),
            record(goodputKibPerSecond = 20.0, macroblockPassRate = 0.90),
            record(goodputKibPerSecond = 30.0, macroblockPassRate = 1.0)
        )
        val oddItems = P1AcceptanceChecklist.evaluate(oddRecords, preset)
        assertEquals("20.0 KiB/s (n=3)", oddItems[1].value)
        assertEquals("90.0%", oddItems[2].value)

        val evenItems = P1AcceptanceChecklist.evaluate(
            oddRecords + record(goodputKibPerSecond = 40.0, macroblockPassRate = 0.80),
            preset
        )
        assertEquals("25.0 KiB/s (n=4)", evenItems[1].value)
        assertEquals("85.0%", evenItems[2].value)
    }

    @Test
    fun exactGoodputPassRateAndThermalThresholdsPass() {
        val items = P1AcceptanceChecklist.evaluate(
            listOf(
                record(goodputKibPerSecond = 60.0, macroblockPassRate = 0.90),
                record(
                    fast60 = true,
                    goodputKibPerSecond = 115.0,
                    elapsedMillis = preset.thermalMillis,
                    firstMinuteKibPerSecond = 100.0,
                    lastMinuteKibPerSecond = 75.0
                )
            ),
            preset
        )

        assertTrue(items[1].passed == true)
        assertTrue(items[2].passed == true)
        assertTrue(items[3].passed == true)
        assertTrue(items[4].passed == true)
        assertEquals("best drop 25%", items[4].value)
    }

    @Test
    fun qualifyingFast60FailureBlocksTheFast60Item() {
        val items = P1AcceptanceChecklist.evaluate(
            listOf(
                record(fast60 = true, goodputKibPerSecond = 120.0),
                record(fast60 = true, sha256Verified = false)
            ),
            preset
        )

        assertTrue(items[3].passed == false)
        assertEquals("120.0 KiB/s (n=1) · failures=1", items[3].value)
        assertNull(items[4].passed)
    }

    @Test
    fun qualifyingThermalFailureRemainsVisibleAsFailedData() {
        val items = P1AcceptanceChecklist.evaluate(
            listOf(
                record(
                    elapsedMillis = preset.thermalMillis,
                    firstMinuteKibPerSecond = 100.0,
                    lastMinuteKibPerSecond = 74.0
                )
            ),
            preset
        )

        assertTrue(items[4].passed == false)
        assertEquals("best drop 26%", items[4].value)
    }

    private fun record(
        fast60: Boolean = false,
        fileSizeBytes: Long = preset.fileBytes,
        sha256Verified: Boolean = true,
        goodputKibPerSecond: Double? = null,
        macroblockPassRate: Double? = null,
        elapsedMillis: Long? = null,
        firstMinuteKibPerSecond: Double? = null,
        lastMinuteKibPerSecond: Double? = null
    ) = P1AcceptanceRecord(
        fast60 = fast60,
        fileSizeBytes = fileSizeBytes,
        sha256Verified = sha256Verified,
        goodputKibPerSecond = goodputKibPerSecond,
        macroblockPassRate = macroblockPassRate,
        elapsedMillis = elapsedMillis,
        firstMinuteKibPerSecond = firstMinuteKibPerSecond,
        lastMinuteKibPerSecond = lastMinuteKibPerSecond
    )
}
