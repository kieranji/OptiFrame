package com.kieran.optiframe.protocol

import java.util.Locale

/** Builds a portable JSON benchmark record from P1 receiver measurements. */
object P1M3BenchmarkReportJson {
    fun encode(
        fileName: String,
        fileSizeBytes: Long,
        expectedSha256: ByteArray,
        sessionId: Long,
        metrics: P1M3TransferMetrics,
        deviceManufacturer: String,
        deviceModel: String,
        androidSdk: Int,
        profileName: String = "P1_OF8_SAFE30",
        targetGoodputKibPerSecond: Int = 60
    ): String {
        require(fileSizeBytes >= 0L)
        require(expectedSha256.size == 32) { "Expected a 32-byte SHA-256 digest" }
        require(sessionId in 0..0xFFFF_FFFFL)
        require(androidSdk > 0)
        require(targetGoodputKibPerSecond > 0)

        val captureUtilization = if (metrics.opticalFramesCaptured == 0L) null else
            metrics.uniqueLogicalFrames.toDouble() / metrics.opticalFramesCaptured
        val json = listOf(
            "\"schema_version\":1",
            "\"profile\":${quote(profileName)}",
            "\"measurement_scope\":\"optical_data_phase_only\"",
            "\"session_id\":$sessionId",
            "\"file_name\":${quote(fileName)}",
            "\"file_size_bytes\":$fileSizeBytes",
            "\"expected_sha256\":${quote(expectedSha256.toHex())}",
            "\"sha256_verified\":${metrics.sha256Verified}",
            "\"elapsed_millis\":${metrics.elapsedMillis ?: "null"}",
            "\"optical_goodput_kib_s\":${decimal(metrics.goodputKibPerSecond)}",
            "\"p1_goodput_target_kib_s\":$targetGoodputKibPerSecond",
            "\"optical_frames_captured\":${metrics.opticalFramesCaptured}",
            "\"unique_logical_frames\":${metrics.uniqueLogicalFrames}",
            "\"duplicate_captures\":${metrics.duplicateCaptures}",
            "\"capture_utilization\":${decimal(captureUtilization)}",
            "\"macroblocks_attempted\":${metrics.macroblocksAttempted}",
            "\"macroblocks_decoded\":${metrics.macroblocksDecoded}",
            "\"macroblocks_failed\":${metrics.macroblocksFailed}",
            "\"macroblock_pass_rate\":${decimal(metrics.macroblockPassRate)}",
            "\"mixed_frames_dropped\":${metrics.mixedFramesDropped}",
            if (profileName == "P1_OF8_SAFE30") {
                "\"p1_safe_macroblock_pass_target\":0.900000"
            } else {
                "\"p1_fast_macroblock_pass_target\":null"
            },
            "\"unique_raptorq_symbols\":${metrics.uniqueSymbolsReceived}",
            "\"device_manufacturer\":${quote(deviceManufacturer)}",
            "\"device_model\":${quote(deviceModel)}",
            "\"android_sdk\":$androidSdk",
            "\"test_setup\":{\"distance_cm\":null,\"fixed_stand\":null}"
        )
        return json.joinToString(prefix = "{\n  ", separator = ",\n  ", postfix = "\n}\n")
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(Locale.ROOT, character.code))
                } else append(character)
            }
        }
        append('"')
    }

    private fun decimal(value: Double?): String = value?.let {
        require(it.isFinite())
        String.format(Locale.US, "%.6f", it)
    } ?: "null"

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}
