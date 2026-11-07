package com.kieran.optiframe.ui.optical

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.LifecycleCameraController

@OptIn(markerClass = [ExperimentalCamera2Interop::class])
class P1CameraThreeAControl(
    private val controller: LifecycleCameraController
) {
    private data class CameraState(val control: CameraControl, val info: CameraInfo)

    @Volatile
    private var fpsRange: Range<Int>? = null

    fun applyFrameRate(fast60: Boolean): String {
        val camera = cameraState() ?: return "camera not ready"
        val targetFps = if (fast60) 60 else 30
        return runCatching {
            val ranges = Camera2CameraInfo.from(camera.info).getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
            )
            val selectedRange = ranges?.map { it.lower..it.upper }
                ?.let { chooseFpsRange(it, targetFps) }
            val range = selectedRange?.let { Range(it.first, it.last) }
                ?: return@runCatching "Camera fps unavailable"
            fpsRange = range
            Camera2CameraControl.from(camera.control)
                .addCaptureRequestOptions(captureRequestOptions(range))
            if (range.upper < targetFps) {
                val profile = if (fast60) "FAST60" else "SAFE30"
                "$profile needs $targetFps fps; camera max ${range.upper}"
            } else {
                "Camera fps ${range.lower}–${range.upper}"
            }
        }.getOrElse { "Camera fps unavailable" }
    }

    fun meter(imageWidth: Int, imageHeight: Int, centerX: Double, centerY: Double) {
        val camera = cameraState() ?: return
        runCatching {
            val point = SurfaceOrientedMeteringPointFactory(
                imageWidth.toFloat(), imageHeight.toFloat()
            ).createPoint(centerX.toFloat(), centerY.toFloat())
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            ).disableAutoCancel().build()
            camera.control.startFocusAndMetering(action)
        }
    }

    fun lock(): String {
        val camera = cameraState() ?: return "camera not ready"
        return runCatching {
            val camera2Info = Camera2CameraInfo.from(camera.info)
            val aeSupported = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE
            ) == true
            val awbSupported = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE
            ) == true
            if (!aeSupported && !awbSupported) return@runCatching "AE lock unsupported"

            val options = CaptureRequestOptions.Builder()
            fpsRange?.let {
                options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
            }
            if (aeSupported) options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
            if (awbSupported) options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
            Camera2CameraControl.from(camera.control).addCaptureRequestOptions(options.build())
            "3A locked · AE ${if (aeSupported) "✓" else "unsupported"} " +
                "AWB ${if (awbSupported) "✓" else "unsupported"}"
        }.getOrElse { "3A lock unavailable" }
    }

    fun unlock() {
        val camera = cameraState() ?: return
        runCatching {
            camera.control.cancelFocusAndMetering()
            val camera2Control = Camera2CameraControl.from(camera.control)
            camera2Control.clearCaptureRequestOptions()
            fpsRange?.let { camera2Control.addCaptureRequestOptions(captureRequestOptions(it)) }
        }
    }

    private fun cameraState(): CameraState? {
        val control = runCatching { controller.cameraControl }.getOrNull() ?: return null
        val info = runCatching { controller.cameraInfo }.getOrNull() ?: return null
        return CameraState(control, info)
    }

    private fun captureRequestOptions(range: Range<Int>): CaptureRequestOptions =
        CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
            .build()

    companion object {
        internal fun chooseFpsRange(ranges: List<IntRange>, targetFps: Int): IntRange? =
            ranges.firstOrNull { it.first == targetFps && it.last == targetFps }
                ?: ranges.asSequence()
                    .filter { it.first <= targetFps && targetFps <= it.last }
                    .maxWithOrNull(compareBy<IntRange> { it.first }.thenByDescending { it.last })
                ?: ranges.maxByOrNull { it.last }
    }
}
