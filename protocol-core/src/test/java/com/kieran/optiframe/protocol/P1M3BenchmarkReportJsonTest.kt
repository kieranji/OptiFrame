package com.kieran.optiframe.protocol

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class P1M3BenchmarkReportJsonTest {
    @Test
    fun encodesVerifiedTransferAndEscapesFileName() {
        val metrics = P1M3TransferMetrics(
            opticalFramesCaptured = 12,
            uniqueLogicalFrames = 8,
            duplicateCaptures = 4,
            macroblocksAttempted = 80,
            macroblocksDecoded = 76,
            macroblocksFailed = 4,
            uniqueSymbolsReceived = 96,
            sha256Verified = true,
            elapsedMillis = 2_000,
            verifiedFileBytes = 4_096,
            goodputKibPerSecond = 2.0
        )

        val json = P1M3BenchmarkReportJson.encode(
            fileName = "sample\"\n.bin",
            fileSizeBytes = 4_096,
            expectedSha256 = ByteArray(32) { it.toByte() },
            sessionId = 42,
            metrics = metrics,
            deviceManufacturer = "Example",
            deviceModel = "Optical Test Phone",
            androidSdk = 36
        )

        assertContains(json, "\"profile\":\"P1_OF8_SAFE30\"")
        assertContains(json, "\"measurement_scope\":\"optical_data_phase_only\"")
        assertContains(json, "\"sha256_verified\":true")
        assertContains(json, "\"capture_utilization\":0.666667")
        assertContains(json, "\"macroblock_pass_rate\":0.950000")
        assertContains(json, "\"p1_goodput_target_kib_s\":60")
        assertContains(json, "\"mixed_frames_dropped\":0")
        assertContains(json, "\"p1_safe_macroblock_pass_target\":0.900000")
        assertContains(json, "\"expected_sha256\":\"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f\"")
        assertContains(json, "\"file_name\":\"sample\\\"\\n.bin\"")
        assertContains(json, "\"test_setup\":{\"distance_cm\":null,\"fixed_stand\":null}")
    }

    @Test
    fun emitsFast60ProfileAndAcceptanceThreshold() {
        val json = P1M3BenchmarkReportJson.encode(
            fileName = "fast.bin",
            fileSizeBytes = 1,
            expectedSha256 = ByteArray(32),
            sessionId = 1,
            metrics = P1M3TransferMetrics(
                opticalFramesCaptured = 5,
                uniqueLogicalFrames = 3,
                duplicateCaptures = 0,
                macroblocksAttempted = 30,
                macroblocksDecoded = 28,
                macroblocksFailed = 2,
                uniqueSymbolsReceived = 12,
                sha256Verified = false,
                elapsedMillis = null,
                verifiedFileBytes = null,
                goodputKibPerSecond = null,
                mixedFramesDropped = 2
            ),
            deviceManufacturer = "Example",
            deviceModel = "FAST",
            androidSdk = 36,
            profileName = "P1_OF8_FAST60",
            targetGoodputKibPerSecond = 115
        )
        assertContains(json, "\"profile\":\"P1_OF8_FAST60\"")
        assertContains(json, "\"p1_goodput_target_kib_s\":115")
        assertContains(json, "\"p1_fast_macroblock_pass_target\":null")
        assertContains(json, "\"mixed_frames_dropped\":2")
    }

    @Test
    fun rejectsDigestWithWrongLength() {
        assertFailsWith<IllegalArgumentException> {
            P1M3BenchmarkReportJson.encode(
                fileName = "sample.bin",
                fileSizeBytes = 0,
                expectedSha256 = ByteArray(31),
                sessionId = 0,
                metrics = P1M3TransferMetrics(
                    opticalFramesCaptured = 0,
                    uniqueLogicalFrames = 0,
                    duplicateCaptures = 0,
                    macroblocksAttempted = 0,
                    macroblocksDecoded = 0,
                    macroblocksFailed = 0,
                    uniqueSymbolsReceived = 0,
                    sha256Verified = false,
                    elapsedMillis = null,
                    verifiedFileBytes = null,
                    goodputKibPerSecond = null
                ),
                deviceManufacturer = "Example",
                deviceModel = "Test",
                androidSdk = 36
            )
        }
    }
}
