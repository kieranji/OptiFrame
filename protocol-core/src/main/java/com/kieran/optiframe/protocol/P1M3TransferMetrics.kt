package com.kieran.optiframe.protocol

/** Receiver-side measurements needed to evaluate the P1 SAFE30 milestone. */
data class P1M3TransferMetrics(
    val opticalFramesCaptured: Long,
    val uniqueLogicalFrames: Long,
    val duplicateCaptures: Long,
    val macroblocksAttempted: Long,
    val macroblocksDecoded: Long,
    val macroblocksFailed: Long,
    val uniqueSymbolsReceived: Long,
    val sha256Verified: Boolean,
    val elapsedMillis: Long?,
    val verifiedFileBytes: Long?,
    val goodputKibPerSecond: Double?,
    val mixedFramesDropped: Long = 0L
) {
    init {
        require(opticalFramesCaptured >= 0L)
        require(uniqueLogicalFrames in 0L..opticalFramesCaptured)
        require(mixedFramesDropped <= opticalFramesCaptured)
        require(duplicateCaptures == opticalFramesCaptured - uniqueLogicalFrames - mixedFramesDropped)
        require(macroblocksAttempted >= 0L)
        require(macroblocksDecoded >= 0L && macroblocksFailed >= 0L)
        require(macroblocksDecoded + macroblocksFailed == macroblocksAttempted)
        require(uniqueSymbolsReceived >= 0L)
        require(mixedFramesDropped >= 0L)
        require(elapsedMillis == null || elapsedMillis >= 0L)
        require(verifiedFileBytes == null || verifiedFileBytes >= 0L)
        require(goodputKibPerSecond == null ||
            (sha256Verified && goodputKibPerSecond.isFinite() && goodputKibPerSecond >= 0.0)
        )
        require(!sha256Verified || verifiedFileBytes != null)
    }

    val macroblockPassRate: Double?
        get() = if (macroblocksAttempted == 0L) null
        else macroblocksDecoded.toDouble() / macroblocksAttempted

    val duplicateCaptureRate: Double?
        get() = if (opticalFramesCaptured == 0L) null
        else duplicateCaptures.toDouble() / opticalFramesCaptured
}
