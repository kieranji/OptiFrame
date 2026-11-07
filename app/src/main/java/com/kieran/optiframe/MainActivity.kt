package com.kieran.optiframe

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kieran.optiframe.protocol.OptiFrameGlobalHeader
import com.kieran.optiframe.protocol.OptiFrameManifest
import com.kieran.optiframe.protocol.OptiFrameManifestCodec
import com.kieran.optiframe.protocol.OptiFrameBootstrapBuilder
import com.kieran.optiframe.protocol.OptiFrameBootstrapCodec
import com.kieran.optiframe.protocol.OptiFrameBootstrapPayload
import com.kieran.optiframe.protocol.P1M3SenderSession
import com.kieran.optiframe.protocol.P1M3TransferMetrics
import com.kieran.optiframe.protocol.P1M3BenchmarkReportJson
import com.kieran.optiframe.protocol.P1ControlMessage
import com.kieran.optiframe.protocol.OptiFrameCapabilities
import com.kieran.optiframe.protocol.ProtocolConstants
import com.kieran.optiframe.protocol.RaptorQSymbol
import com.kieran.optiframe.protocol.OpenRqRaptorQ
import com.kieran.optiframe.ui.optical.P1OpticalDisplay
import com.kieran.optiframe.ui.optical.P1Safe30DisplayState
import com.kieran.optiframe.ui.optical.P1CameraPreview
import com.kieran.optiframe.ui.optical.P1CameraReceivePipeline
import com.kieran.optiframe.ui.optical.P1PerspectiveSampler
import com.kieran.optiframe.ui.optical.P1AnchorDetector
import com.kieran.optiframe.ui.optical.P1BootstrapQr
import com.kieran.optiframe.ui.ble.P1BleControlClient
import com.kieran.optiframe.ui.ble.P1BleControlServer
import com.kieran.optiframe.ui.theme.OptiFrameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Locale
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OptiFrameTheme {
                SenderHome(
                    readFile = { uri -> readSelectedFile(uri) },
                    readManifest = { uri -> readManifest(uri) },
                    readBootstrap = { uri -> readBootstrap(uri) },
                    writeManifest = { uri, manifest -> writeManifest(uri, manifest) },
                    writeBootstrap = { uri, payload -> writeBootstrap(uri, payload) },
                    writeReceivedFile = { uri, file -> writeReceivedFile(uri, file) },
                    writeTextDocument = { uri, text -> writeTextDocument(uri, text) }
                )
            }
        }
    }

    private suspend fun readSelectedFile(uri: Uri): SelectedFile = withContext(Dispatchers.IO) {
        val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "file.bin"
        val mimeType = contentResolver.getType(uri)
        val file = File(cacheDir, "optiframe-source-${UUID.randomUUID()}.bin")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().buffered().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: error("Could not open the selected file")
            SelectedFile(name, mimeType, file)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private suspend fun readManifest(uri: Uri): OptiFrameManifest = withContext(Dispatchers.IO) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read manifest")
        OptiFrameManifestCodec.decode(bytes)
    }

    private suspend fun readBootstrap(uri: Uri): OptiFrameBootstrapPayload = withContext(Dispatchers.IO) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read bootstrap payload")
        OptiFrameBootstrapCodec.decode(bytes)
    }

    private suspend fun writeManifest(uri: Uri, manifest: OptiFrameManifest) = withContext(Dispatchers.IO) {
        val bytes = OptiFrameManifestCodec.encode(manifest)
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("Could not write manifest")
    }

    private suspend fun writeBootstrap(uri: Uri, payload: OptiFrameBootstrapPayload) = withContext(Dispatchers.IO) {
        val bytes = OptiFrameBootstrapCodec.encode(payload)
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("Could not write bootstrap payload")
    }

    private suspend fun writeReceivedFile(uri: Uri, file: File) = withContext(Dispatchers.IO) {
        val output = contentResolver.openOutputStream(uri, "wt")
            ?: error("Could not save received file")
        output.use { destination -> FileInputStream(file).use { source -> source.copyTo(destination) } }
    }

    private suspend fun writeTextDocument(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Could not save benchmark report")
    }
}

private data class SelectedFile(val name: String, val mimeType: String?, val file: File)

@Composable
private fun SenderHome(
    readFile: suspend (Uri) -> SelectedFile,
    readManifest: suspend (Uri) -> OptiFrameManifest,
    readBootstrap: suspend (Uri) -> OptiFrameBootstrapPayload,
    writeManifest: suspend (Uri, OptiFrameManifest) -> Unit,
    writeBootstrap: suspend (Uri, OptiFrameBootstrapPayload) -> Unit,
    writeReceivedFile: suspend (Uri, File) -> Unit,
    writeTextDocument: suspend (Uri, String) -> Unit
) {
    var session by remember { mutableStateOf<P1M3SenderSession?>(null) }
    var senderSourceFile by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) {
        onDispose { senderSourceFile?.delete() }
    }
    var fast60Selected by remember { mutableStateOf(false) }
    var bleServer by remember { mutableStateOf<P1BleControlServer?>(null) }
    var bleClient by remember { mutableStateOf<P1BleControlClient?>(null) }
    var status by remember { mutableStateOf("Choose a file to prepare a P1 SAFE30 transfer.") }
    var busy by remember { mutableStateOf(false) }
    var transferState by remember { mutableStateOf<P1Safe30DisplayState?>(null) }
    var receiving by remember { mutableStateOf(false) }
    var cameraStatus by remember { mutableStateOf("Camera is not active") }
    var importedManifest by remember { mutableStateOf<OptiFrameManifest?>(null) }
    var importedBootstrap by remember { mutableStateOf<OptiFrameBootstrapPayload?>(null) }
    var bootstrapPayload by remember { mutableStateOf<OptiFrameBootstrapPayload?>(null) }
    var manifestStatus by remember { mutableStateOf("Import the sender's .ofm manifest to start receiving.") }
    var verifiedFile by remember { mutableStateOf<File?>(null) }
    var receiveMetrics by remember { mutableStateOf<P1M3TransferMetrics?>(null) }
    var calibratedAnchors by remember { mutableStateOf<P1PerspectiveSampler.AnchorPoints?>(null) }
    var showBootstrapQr by remember { mutableStateOf(false) }
    var bleStatus by remember { mutableStateOf("BLE control is not connected") }
    val qrFrameCounter = remember { AtomicInteger(0) }
    val lastAutoDetection = remember { AtomicReference<P1AnchorDetector.Detection?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentBleClient by rememberUpdatedState(bleClient)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                session = null
                    bleServer?.close()
                    bleServer = null
                    senderSourceFile?.delete()
                senderSourceFile = null
                status = "Reading file and preparing transfer…"
                var pendingSourceFile: File? = null
                try {
                    val selected = readFile(uri)
                    pendingSourceFile = selected.file
                    val created = withContext(Dispatchers.Default) {
                        val id = UUID.randomUUID().mostSignificantBits and 0xFFFF_FFFFL
                        OpenRqRaptorQ.m3SenderSessionFromFile(
                            fileName = selected.name,
                            file = selected.file,
                            initialRepairSymbolsPerBlock = 0,
                            initialRepairFraction = if (fast60Selected) 0.10 else 0.20,
                            advanceAfterRepairQueue = !fast60Selected,
                            sessionId = id,
                            firstFrameSequence = 0,
                            flagsForItem = { 0 },
                            headerFactory = { block, sequence, kind ->
                                OptiFrameGlobalHeader(
                                    profileId = ProtocolConstants.PROFILE_P1_ID,
                                    sessionId = id,
                                    frameSequence = sequence,
                                    sourceBlockNumber = block,
                                    paletteId = ProtocolConstants.PALETTE_OF8_FIXED,
                                    frameType = if (kind == RaptorQSymbol.Kind.SOURCE)
                                        ProtocolConstants.FRAME_TYPE_DATA else ProtocolConstants.FRAME_TYPE_REPAIR,
                                    flags = 0,
                                    calibrationEpoch = 0
                                )
                            },
                            mimeType = selected.mimeType,
                            fast60 = fast60Selected
                        )
                    }
                    val bootstrap = withContext(Dispatchers.Default) {
                        OptiFrameBootstrapBuilder.build(
                            sessionId = created.sessionId,
                            fileSize = created.manifest.fileSize,
                            fileSha256 = created.manifest.fileSha256,
                            manifest = created.manifest,
                            capabilityFlags = if (fast60Selected)
                                OptiFrameCapabilities.P1_FAST60_BLE_CONTROL else 0L
                        )
                    }
                    if (created.fast60) {
                        val server = P1BleControlServer(
                            context.applicationContext,
                            created.sessionId,
                            onMessage = { message -> created.applyControlMessage(message) },
                            onState = { bleStatus = it }
                        )
                        server.start()
                        bleServer = server
                    }
                    session = created
                    senderSourceFile = selected.file
                    bootstrapPayload = bootstrap
                    showBootstrapQr = true
                    transferState = null
                    status = "Bootstrap ready: ${created.manifest.fileName} (${created.manifest.fileSize} bytes)"
                } catch (error: Exception) {
                    bleServer?.close()
                    bleServer = null
                    pendingSourceFile?.delete()
                    status = "Could not prepare file: ${error.message ?: "unknown error"}"
                } finally {
                    busy = false
                }
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            receiving = true
            cameraStatus = "Starting camera…"
        } else {
            cameraStatus = "Camera permission is required to receive a transfer"
        }
    }
    val m4SenderPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) {
            fast60Selected = true
            picker.launch(arrayOf("*/*"))
        } else status = "Bluetooth advertise/connect permissions are required for M4."
    }
    val receiveFile = remember(importedManifest, importedBootstrap) {
        val bootstrap = importedBootstrap
        bootstrap?.let { File(context.cacheDir, "optiframe-${it.sessionId}.received") }
    }
    val receivePipeline = remember(importedManifest, importedBootstrap, receiveFile) {
        val manifest = importedManifest
        val bootstrap = importedBootstrap
        val outputFile = receiveFile
        if (manifest != null && bootstrap != null && outputFile != null) {
            outputFile.delete()
            val m4Control = (bootstrap.capabilityFlags and
                OptiFrameCapabilities.P1_FAST60_BLE_CONTROL) != 0L
            P1CameraReceivePipeline(
                manifest = manifest,
                bootstrap = bootstrap,
                onFeedback = { feedback ->
                    if (m4Control) {
                        val message = if (feedback.blockDecoded) {
                            P1ControlMessage.BlockDecoded(
                                bootstrap.sessionId,
                                feedback.sourceBlockNumber,
                                feedback.uniqueSymbolsReceived
                            )
                        } else {
                            P1ControlMessage.RepairRequested(
                                bootstrap.sessionId,
                                feedback.sourceBlockNumber,
                                feedback.additionalRepairSymbols,
                                feedback.uniqueSymbolsReceived
                            )
                        }
                        runCatching { currentBleClient?.send(message) }
                    }
                },
                outputFile = outputFile,
                requestRepairsOnlyOnSlotFailures = m4Control,
                onTransferComplete = {
                    if (m4Control) runCatching {
                        currentBleClient?.send(
                            P1ControlMessage.Command(
                                bootstrap.sessionId,
                                P1ControlMessage.Action.TRANSFER_COMPLETE
                            )
                        )
                    }
                }
            )
        } else null
    }

    val m4ReceiverPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) {
            val bootstrap = importedBootstrap
            if (bootstrap != null) {
                runCatching { bleClient?.close() }
                val client = P1BleControlClient(
                    context.applicationContext,
                    bootstrap.sessionId,
                    onState = { bleStatus = it }
                )
                bleClient = client
                runCatching { client.startScan() }
                    .onFailure { bleStatus = "BLE scan could not start: ${it.message ?: "error"}" }
                if (runCatching { receivePipeline?.finishIfComplete() }.getOrNull() != null) {
                    runCatching {
                        client.send(
                            P1ControlMessage.Command(
                                bootstrap.sessionId,
                                P1ControlMessage.Action.TRANSFER_COMPLETE
                            )
                        )
                    }
                }
            }
        } else bleStatus = "Bluetooth scan/connect permissions are required for M4."
    }
    val manifestExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val selected = session
        if (uri != null && selected != null) scope.launch {
            try {
                writeManifest(uri, selected.manifest)
                status = "Manifest saved. Import this file on the receiver device."
            } catch (error: Exception) {
                status = "Manifest export failed: ${error.message ?: "unknown error"}"
            }
        }
    }
    val bootstrapExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = bootstrapPayload
        if (uri != null && payload != null) scope.launch {
            try {
                writeBootstrap(uri, payload)
                status = "Bootstrap saved. Import it with the manifest on the receiver device."
            } catch (error: Exception) {
                status = "Bootstrap export failed: ${error.message ?: "unknown error"}"
            }
        }
    }
    val manifestPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                importedManifest = readManifest(uri)
                bleClient?.close()
                bleClient = null
                bleStatus = "BLE control is not connected"
                importedBootstrap = null
                verifiedFile = null
                receiveMetrics = null
                calibratedAnchors = null
                lastAutoDetection.set(null)
                manifestStatus = "Ready to receive ${importedManifest?.fileName ?: "file"}"
            } catch (error: Exception) {
                importedManifest = null
                importedBootstrap = null
                verifiedFile = null
                receiveMetrics = null
                calibratedAnchors = null
                lastAutoDetection.set(null)
                manifestStatus = "Invalid manifest: ${error.message ?: "unknown error"}"
            }
        }
    }
    val bootstrapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val candidate = readBootstrap(uri)
                val manifest = importedManifest ?: error("Import the manifest first")
                require(candidate.fileSize <= Int.MAX_VALUE.toULong() && candidate.fileSize.toLong() == manifest.fileSize) {
                    "Bootstrap and manifest file sizes differ"
                }
                require(candidate.fileSha256.contentEquals(manifest.fileSha256)) {
                    "Bootstrap and manifest file digests differ"
                }
                require(candidate.manifestHash.contentEquals(OptiFrameManifestCodec.hash(manifest))) {
                    "Bootstrap does not refer to this manifest"
                }
                importedBootstrap = candidate
                verifiedFile = null
                receiveMetrics = null
                manifestStatus = "Bootstrap and manifest verified for ${manifest.fileName}"
            } catch (error: Exception) {
                importedBootstrap = null
                manifestStatus = "Invalid bootstrap: ${error.message ?: "unknown error"}"
            }
        }
    }
    val receivedFileSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = verifiedFile
        if (uri != null && file != null) scope.launch {
            try {
                writeReceivedFile(uri, file)
                cameraStatus = "Received file saved successfully."
            } catch (error: Exception) {
                cameraStatus = "Saving failed: ${error.message ?: "unknown error"}"
            }
        }
    }
    val reportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val metrics = receiveMetrics
        val manifest = importedManifest
        val bootstrap = importedBootstrap
        if (uri != null && metrics != null && manifest != null && bootstrap != null) scope.launch {
            try {
                val fast60Report = (bootstrap.capabilityFlags and
                    OptiFrameCapabilities.P1_FAST60_BLE_CONTROL) != 0L
                val report = P1M3BenchmarkReportJson.encode(
                    fileName = manifest.fileName,
                    fileSizeBytes = manifest.fileSize,
                    expectedSha256 = manifest.fileSha256,
                    sessionId = bootstrap.sessionId,
                    metrics = metrics,
                    deviceManufacturer = Build.MANUFACTURER,
                    deviceModel = Build.MODEL,
                    androidSdk = Build.VERSION.SDK_INT,
                    profileName = if (fast60Report) "P1_OF8_FAST60" else "P1_OF8_SAFE30",
                    targetGoodputKibPerSecond = if (fast60Report) 115 else 60
                )
                writeTextDocument(uri, report)
                cameraStatus = "Benchmark report saved. Add distance and fixed-stand details to the test log."
            } catch (error: Exception) {
                cameraStatus = "Benchmark report failed: ${error.message ?: "unknown error"}"
            }
        }
    }
    LaunchedEffect(receivePipeline) {
        val outcome = runCatching { receivePipeline?.finishIfComplete() }
        val result = outcome.getOrNull()
        if (result != null && verifiedFile == null) {
            verifiedFile = receiveFile
            receiveMetrics = result.metrics
            cameraStatus = "SHA-256 verified · ${importedManifest?.fileSize ?: 0} bytes received."
        } else outcome.exceptionOrNull()?.let {
            cameraStatus = "Receiver error: ${it.message ?: "invalid session"}"
        }
    }

    val active = session
    if (receiving) {
        Column(modifier = Modifier.fillMaxSize()) {
            P1CameraPreview(
                modifier = Modifier.weight(1f),
                onAnchorsChanged = { anchors ->
                    if (anchors == null) lastAutoDetection.set(null)
                    calibratedAnchors = anchors
                    cameraStatus = if (anchors == null) "Tap the four corner-anchor centers clockwise, starting at top-left."
                    else "Geometry calibrated; align and hold the optical frame steady."
                },
                onCalibrationProgress = { count ->
                    if (calibratedAnchors == null) cameraStatus = "Calibration: tap anchor ${count + 1} of 4."
                },
                onImage = { image: ImageProxy ->
                if (cameraStatus == "Camera ready" || cameraStatus == "Starting camera…") {
                    cameraStatus = "Tap the four anchor centers clockwise, starting at top-left."
                }
                val pipeline = receivePipeline
                if (pipeline == null && importedManifest != null && importedBootstrap == null &&
                    qrFrameCounter.incrementAndGet() % 3 == 0
                ) {
                    val scanned = P1BootstrapQr.scan(image)
                    if (scanned != null) {
                        val manifest = importedManifest
                        if (manifest != null && scanned.fileSize <= Int.MAX_VALUE.toULong() &&
                            scanned.fileSize.toLong() == manifest.fileSize &&
                            scanned.fileSha256.contentEquals(manifest.fileSha256) &&
                            scanned.manifestHash.contentEquals(OptiFrameManifestCodec.hash(manifest))
                        ) {
                            importedBootstrap = scanned
                            verifiedFile = null
                            receiveMetrics = null
                            manifestStatus = "Bootstrap QR verified for ${manifest.fileName}"
                            cameraStatus = "Bootstrap accepted; align the sender's optical matrix."
                        } else {
                            cameraStatus = "QR found, but it does not match the imported manifest."
                        }
                    }
                }
                val manualAnchors = calibratedAnchors
                var autoDetection: P1AnchorDetector.Detection? = null
                if (pipeline != null && verifiedFile == null && manualAnchors == null &&
                    image.planes.size == 3
                ) {
                    val yPlane = image.planes[0]
                    val buffer = yPlane.buffer.duplicate()
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    val planeView = P1PerspectiveSampler.PlaneView(
                        data, yPlane.rowStride, yPlane.pixelStride, image.width, image.height
                    )
                    autoDetection = P1AnchorDetector.detect(planeView)
                        ?.also { lastAutoDetection.set(it) }
                        ?: lastAutoDetection.get()
                    if (autoDetection != null &&
                        !cameraStatus.startsWith("SHA-256 verified") &&
                        !cameraStatus.startsWith("Receiving")
                    ) {
                        cameraStatus = String.format(
                            Locale.US,
                            "Auto anchors · P95 %.2f cell",
                            autoDetection.p95ErrorCells
                        )
                    }
                }
                val anchors = manualAnchors ?: autoDetection?.anchors
                if (pipeline != null && anchors != null && verifiedFile == null) {
                    try {
                        val result = pipeline.accept(image, anchors)
                        if (result.verified) {
                            verifiedFile = receiveFile
                            receiveMetrics = result.metrics
                            cameraStatus = "SHA-256 verified · ${importedManifest?.fileSize ?: 0} bytes received."
                        } else if (cameraStatus.startsWith("Geometry calibrated") ||
                            cameraStatus.startsWith("Auto anchors")
                        ) {
                            cameraStatus = "Receiving · ${result.metrics.macroblocksDecoded} macroblocks decoded."
                        }
                    } catch (error: IllegalArgumentException) {
                        if (cameraStatus.startsWith("Geometry calibrated") ||
                            cameraStatus.startsWith("Auto anchors") ||
                            cameraStatus.startsWith("Receiving")
                        ) {
                            cameraStatus = "Looking for a valid P1 frame: ${error.message ?: "decode failed"}"
                        }
                    } catch (error: IllegalStateException) {
                        cameraStatus = "Receiver error: ${error.message ?: "invalid session"}"
                    }
                }
                }
            )
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("OptiFrame receiver", color = Color.White)
                Text(cameraStatus, color = Color.White)
                val m4Control = importedBootstrap?.let {
                    (it.capabilityFlags and OptiFrameCapabilities.P1_FAST60_BLE_CONTROL) != 0L
                } == true
                if (m4Control) {
                    Text(bleStatus, color = Color.White)
                    if (bleStatus != "BLE feedback connected") {
                        OutlinedButton(onClick = {
                            val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(
                                android.Manifest.permission.BLUETOOTH_SCAN,
                                android.Manifest.permission.BLUETOOTH_CONNECT
                            ) else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            m4ReceiverPermissionLauncher.launch(permissions)
                        }) { Text("Connect BLE feedback") }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = {
                                importedBootstrap?.let { bootstrap ->
                                    runCatching { bleClient?.send(P1ControlMessage.Command(bootstrap.sessionId, P1ControlMessage.Action.PAUSE)) }
                                }
                            }) { Text("Pause sender") }
                            OutlinedButton(onClick = {
                                importedBootstrap?.let { bootstrap ->
                                    runCatching { bleClient?.send(P1ControlMessage.Command(bootstrap.sessionId, P1ControlMessage.Action.RESUME)) }
                                }
                            }) { Text("Resume sender") }
                            OutlinedButton(onClick = {
                                importedBootstrap?.let { bootstrap ->
                                    runCatching { bleClient?.send(P1ControlMessage.Command(bootstrap.sessionId, P1ControlMessage.Action.CANCEL)) }
                                }
                            }) { Text("Cancel transfer") }
                        }
                    }
                }
                Text("Tap the center of each 6×6 corner anchor clockwise: top-left, top-right, bottom-right, bottom-left. Repeat to recalibrate.", color = Color.White)
                if (verifiedFile != null) {
                    receiveMetrics?.let { metrics ->
                        val rate = metrics.goodputKibPerSecond?.let { "%.1f KiB/s".format(it) } ?: "n/a"
                        val passRate = metrics.macroblockPassRate?.let { "%.1f%%".format(it * 100.0) } ?: "n/a"
                        Text(
                            if (m4Control) "Goodput: $rate (P1 FAST target ≥115 KiB/s) · macroblock pass: $passRate"
                            else "Goodput: $rate (target ≥60 KiB/s) · macroblock pass: $passRate (target ≥90%)",
                            color = Color.White
                        )
                    }
                    Button(onClick = {
                        receivedFileSaver.launch(importedManifest?.fileName ?: "received.bin")
                    }) { Text("Save verified file") }
                    Button(onClick = {
                        val name = importedManifest?.fileName?.substringBeforeLast('.') ?: "transfer"
                        reportSaver.launch("${name}_${if (m4Control) "m4" else "m3"}_report.json")
                    }) { Text("Export JSON benchmark report") }
                }
                Button(onClick = {
                    receiving = false
                    calibratedAnchors = null
                    lastAutoDetection.set(null)
                }) { Text("Stop camera") }
            }
        }
    } else if (active != null) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (showBootstrapQr && bootstrapPayload != null) {
                val qr = remember(bootstrapPayload) { P1BootstrapQr.render(checkNotNull(bootstrapPayload)) }
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "OptiFrame transfer bootstrap QR code",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            } else {
                P1OpticalDisplay(
                    session = active,
                    modifier = Modifier.weight(1f),
                    onStateChange = {
                        transferState = it
                        if (it == P1Safe30DisplayState.COMPLETE || it == P1Safe30DisplayState.CANCELLED) {
                            bleServer?.close()
                            bleServer = null
                        }
                    }
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("${active.manifest.fileName} · ${active.progress.completedBlocks}/${active.progress.totalBlocks} blocks", color = Color.White)
                Text(if (showBootstrapQr) {
                    "Receiver setup: scan this QR, import the manifest, then connect BLE feedback for M4."
                } else when (transferState) {
                    P1Safe30DisplayState.DISPLAYING -> if (active.fast60) "Displaying P1 FAST60 frames" else "Displaying SAFE30 frames"
                    P1Safe30DisplayState.WAITING_FOR_FEEDBACK -> if (active.fast60) "Waiting for BLE block feedback" else "Waiting for receiver feedback"
                    P1Safe30DisplayState.PAUSED -> "Paused by receiver over BLE"
                    P1Safe30DisplayState.CANCELLED -> "Transfer cancelled"
                    P1Safe30DisplayState.COMPLETE -> if (active.fast60)
                        "Receiver completed file verification"
                    else "All planned SAFE30 frames sent; check receiver SHA-256 status"
                    null -> "Starting transfer"
                }, color = Color.White)
                OutlinedButton(onClick = {
                    manifestExport.launch("${active.manifest.fileName}.ofm")
                }) { Text("Export receiver manifest") }
                OutlinedButton(onClick = {
                    bootstrapExport.launch("${active.manifest.fileName}.ofb")
                }) { Text("Export bootstrap") }
                if (showBootstrapQr) {
                    Button(onClick = { showBootstrapQr = false }) { Text(if (active.fast60) "Start FAST60" else "Start SAFE30") }
                } else {
                    OutlinedButton(onClick = { showBootstrapQr = true }) { Text("Pause and show bootstrap QR") }
                }
                Button(onClick = {
                    runCatching {
                        active.applyControlMessage(P1ControlMessage.Command(active.sessionId, P1ControlMessage.Action.CANCEL))
                    }
                    bleServer?.close()
                    bleServer = null
                    session = null
                    transferState = null
                    senderSourceFile?.delete()
                    senderSourceFile = null
                }) { Text("Stop") }
            }
        }
    } else {
        Scaffold { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("OptiFrame", style = MaterialTheme.typography.headlineMedium)
                Text(status, modifier = Modifier.padding(vertical = 16.dp))
                Button(enabled = !busy, onClick = { fast60Selected = false; picker.launch(arrayOf("*/*")) }) {
                    Text(if (busy) "Preparing…" else "Choose file")
                }
                OutlinedButton(enabled = !busy, onClick = {
                    val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(
                        android.Manifest.permission.BLUETOOTH_ADVERTISE,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    m4SenderPermissionLauncher.launch(permissions)
                }) { Text("Prepare experimental M4 FAST60 + BLE") }
                OutlinedButton(onClick = {
                    manifestPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                }) { Text("Import receiver manifest") }
                Text(manifestStatus, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = importedManifest != null, onClick = {
                    bootstrapPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                }) { Text("Import sender bootstrap") }
                OutlinedButton(onClick = {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                },
                    enabled = importedManifest != null && (
                        importedBootstrap?.let { (it.capabilityFlags and OptiFrameCapabilities.P1_FAST60_BLE_CONTROL) == 0L } != false ||
                            bleStatus == "BLE feedback connected"
                        )
                ) { Text("Start receiver camera") }
                Text(
                    "M3 SAFE30 and experimental M4 FAST60. FAST60 depends on BLE feedback and device timing; calibrate four corner anchors.",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
