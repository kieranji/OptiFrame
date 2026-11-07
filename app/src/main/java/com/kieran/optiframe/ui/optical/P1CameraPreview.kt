package com.kieran.optiframe.ui.optical

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.graphics.PointF
import android.util.Size
import android.view.MotionEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Live receiver preview with bounded, latest-frame-only analysis delivery. */
@Composable
fun P1CameraPreview(
    modifier: Modifier = Modifier,
    onImage: (ImageProxy) -> Unit,
    onAnchorsChanged: (P1PerspectiveSampler.AnchorPoints?) -> Unit,
    onCalibrationProgress: (Int) -> Unit = {},
    onThreeAControl: (P1CameraThreeAControl) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = androidx.compose.runtime.remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // The 640x480 analysis default leaves ~2 px per P1 cell and blurs the pilots together.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            setImageAnalysisOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        }
    }
    val analyzerExecutor: ExecutorService = androidx.compose.runtime.remember {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "optiframe-camera-analysis").apply { isDaemon = true }
        }
    }
    val latestOnImage = rememberUpdatedState(onImage)
    val latestOnAnchors = rememberUpdatedState(onAnchorsChanged)
    val latestOnProgress = rememberUpdatedState(onCalibrationProgress)
    val latestOnThreeAControl = rememberUpdatedState(onThreeAControl)
    val threeAControl = remember(controller) { P1CameraThreeAControl(controller) }
    val imageTransform = remember { AtomicReference<OutputTransform?>(null) }
    val calibrationPoints = remember { mutableStateListOf<PointF>() }
    val transformFactory = remember {
        ImageProxyTransformFactory().apply {
            setUsingCropRect(false)
            setUsingRotationDegrees(false)
        }
    }

    DisposableEffect(controller, lifecycleOwner, analyzerExecutor) {
        controller.bindToLifecycle(lifecycleOwner)
        latestOnThreeAControl.value(threeAControl)
        controller.setImageAnalysisAnalyzer(analyzerExecutor) { image ->
            try {
                imageTransform.set(transformFactory.getOutputTransform(image))
                latestOnImage.value(image)
            } finally {
                image.close()
            }
        }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
                setOnTouchListener { view, event ->
                    if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true
                    val viewTransform = (view as PreviewView).outputTransform
                    val frameTransform = imageTransform.get()
                    if (viewTransform == null || frameTransform == null) return@setOnTouchListener true
                    try {
                        val point = PointF(event.x, event.y)
                        CoordinateTransform(viewTransform, frameTransform).mapPoint(point)
                        if (calibrationPoints.size == 4) {
                            calibrationPoints.clear()
                            latestOnAnchors.value(null)
                        }
                        calibrationPoints.add(point)
                        latestOnProgress.value(calibrationPoints.size)
                        if (calibrationPoints.size == 4) {
                            latestOnAnchors.value(
                                P1PerspectiveSampler.AnchorPoints(
                                    topLeftX = calibrationPoints[0].x.toDouble(),
                                    topLeftY = calibrationPoints[0].y.toDouble(),
                                    topRightX = calibrationPoints[1].x.toDouble(),
                                    topRightY = calibrationPoints[1].y.toDouble(),
                                    bottomRightX = calibrationPoints[2].x.toDouble(),
                                    bottomRightY = calibrationPoints[2].y.toDouble(),
                                    bottomLeftX = calibrationPoints[3].x.toDouble(),
                                    bottomLeftY = calibrationPoints[3].y.toDouble()
                                )
                            )
                        }
                    } catch (_: IllegalArgumentException) {
                        calibrationPoints.clear()
                        latestOnProgress.value(0)
                    }
                    true
                }
            }
        },
        update = { it.controller = controller }
    )
}
