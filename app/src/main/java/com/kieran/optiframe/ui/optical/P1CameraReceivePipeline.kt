package com.kieran.optiframe.ui.optical

import androidx.camera.core.ImageProxy
import java.io.File
import com.kieran.optiframe.protocol.OptiFrameManifest
import com.kieran.optiframe.protocol.OptiFrameManifestCodec
import com.kieran.optiframe.protocol.OptiFrameBootstrapPayload
import com.kieran.optiframe.protocol.OpenRqRaptorQ
import com.kieran.optiframe.protocol.P1M3ReceiverSession
import com.kieran.optiframe.protocol.P1M3TransferMetrics
import com.kieran.optiframe.protocol.P1CompositeFrameException
import com.kieran.optiframe.protocol.RaptorQBlockFeedback
import com.kieran.optiframe.protocol.P1ReceiverStallWatchdog

/** Camera frame -> perspective sampling -> P1 decode -> RaptorQ file verification. */
class P1CameraReceivePipeline(
    private val manifest: OptiFrameManifest,
    private val bootstrap: OptiFrameBootstrapPayload,
    private val onFeedback: (RaptorQBlockFeedback) -> Unit = {},
    private val erasureThreshold: Double = DEFAULT_ERASURE_THRESHOLD,
    private val outputFile: File? = null,
    private val onTransferComplete: () -> Unit = {},
    private val requestRepairsOnlyOnSlotFailures: Boolean = false,
    private val stallWatchdog: P1ReceiverStallWatchdog? = null
) {
    data class FrameResult(
        val sessionId: Long,
        val verifiedFile: ByteArray?,
        val verified: Boolean,
        val metrics: P1M3TransferMetrics
    )

    private val receiver: P1M3ReceiverSession by lazy(LazyThreadSafetyMode.NONE) {
        OpenRqRaptorQ.m3ReceiverSession(
            manifest,
            bootstrap.sessionId,
            spoolFile = outputFile?.let { File("${it.path}.part") }
        )
    }
    private var completedFile: ByteArray? = null
    private var completed = false
    private var mixedFramesDropped = 0L

    init {
        require(erasureThreshold in 0.0..1.0)
        require(bootstrap.fileSize <= Int.MAX_VALUE.toULong() && bootstrap.fileSize.toLong() == manifest.fileSize) {
            "Bootstrap file size does not match manifest"
        }
        require(bootstrap.fileSha256.contentEquals(manifest.fileSha256)) {
            "Bootstrap file digest does not match manifest"
        }
        require(bootstrap.manifestHash.contentEquals(OptiFrameManifestCodec.hash(manifest))) {
            "Bootstrap manifest hash does not match the imported manifest"
        }
    }

    @Synchronized
    fun accept(image: ImageProxy, anchors: P1PerspectiveSampler.AnchorPoints): FrameResult {
        if (completed) {
            return FrameResult(bootstrap.sessionId, completedFile, true, currentMetrics())
        }

        val decoded = try {
            P1PerspectiveSampler.decode(image, anchors)
        } catch (error: P1CompositeFrameException) {
            mixedFramesDropped++
            throw error
        }
        require(decoded.frame.header.sessionId == bootstrap.sessionId) {
            "Optical frame session ID does not match bootstrap"
        }
        val result = receiver.acceptVsyncFrame(
            frame = decoded.frame,
            fullGridConfidence = decoded.cellConfidence,
            erasureThreshold = erasureThreshold,
            requestRepairsOnlyOnSlotFailures = requestRepairsOnlyOnSlotFailures
        )
        stallWatchdog?.onFrame(
            decoded.frame.header.sourceBlockNumber,
            result,
            System.nanoTime()
        )
        result.feedback?.let(onFeedback)
        if (receiver.isComplete) {
            finish()
        }
        return FrameResult(
            sessionId = bootstrap.sessionId,
            verifiedFile = completedFile,
            verified = completed,
            metrics = currentMetrics()
        )
    }

    @Synchronized
    fun pollStall(nowNanos: Long = System.nanoTime()) {
        if (completed) return
        val block = stallWatchdog?.poll(nowNanos) ?: return
        receiver.stallRepairFeedback(block)?.let(onFeedback)
    }

    /** Completes a zero-source-block (empty) transfer before any frame arrives. */
    @Synchronized
    fun finishIfComplete(): FrameResult? {
        if (completed) return FrameResult(bootstrap.sessionId, completedFile, true, currentMetrics())
        if (!receiver.isComplete) return null
        finish()
        return FrameResult(bootstrap.sessionId, completedFile, true, currentMetrics())
    }

    private fun finish() {
        try {
            if (outputFile == null) {
                completedFile = receiver.finishAndVerifyFile()
            } else {
                val spooledFile = receiver.finishAndVerifySpooledFile()
                outputFile.delete()
                if (!spooledFile.renameTo(outputFile)) {
                    spooledFile.copyTo(outputFile, overwrite = true)
                    spooledFile.delete()
                }
            }
        } catch (error: Exception) {
            outputFile?.delete()
            outputFile?.let { File("${it.path}.part").delete() }
            runCatching { receiver.close() }
            throw error
        }
        completed = true
        runCatching(onTransferComplete)
    }

    private fun currentMetrics(): P1M3TransferMetrics {
        val metrics = receiver.metrics
        return metrics.copy(
            opticalFramesCaptured = metrics.opticalFramesCaptured + mixedFramesDropped,
            mixedFramesDropped = mixedFramesDropped
        )
    }

    companion object {
        /** Low-margin color decisions become byte erasures for inner RS recovery. */
        const val DEFAULT_ERASURE_THRESHOLD: Double = 0.20
    }
}
