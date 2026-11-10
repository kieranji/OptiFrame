package com.kieran.optiframe

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.kieran.optiframe.protocol.OptiFrameBootstrapBuilder
import com.kieran.optiframe.protocol.OptiFrameBootstrapCodec
import com.kieran.optiframe.protocol.OptiFrameBootstrapPayload
import com.kieran.optiframe.protocol.OptiFrameManifest
import com.kieran.optiframe.protocol.OptiFrameManifestCodec
import com.kieran.optiframe.ui.optical.P1BootstrapQr
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P1BootstrapQrTest {
    private fun manifest(
        fileName: String = "transfer.bin",
        fileSize: Long = 4096L,
        sourceBlockKValues: IntArray = intArrayOf(16)
    ) = OptiFrameManifest(
        fileName = fileName,
        fileSize = fileSize,
        fileSha256 = ByteArray(32) { it.toByte() },
        sourceSymbolBytes = 256,
        sourceBlockKValues = sourceBlockKValues
    )

    private fun bootstrap(manifest: OptiFrameManifest) = OptiFrameBootstrapBuilder.build(
        sessionId = 0x12345678L,
        fileSize = manifest.fileSize,
        fileSha256 = manifest.fileSha256,
        manifest = manifest,
        capabilityFlags = 0L
    )

    private fun assertSameTransfer(
        expectedBootstrap: OptiFrameBootstrapPayload,
        expectedManifest: OptiFrameManifest,
        actual: P1BootstrapQr.ScanResult.Transfer
    ) {
        assertEquals(expectedBootstrap.sessionId, actual.bootstrap.sessionId)
        assertEquals(expectedBootstrap.fileSize.toLong(), actual.bootstrap.fileSize.toLong())
        assertArrayEquals(expectedBootstrap.fileSha256, actual.bootstrap.fileSha256)
        assertArrayEquals(expectedBootstrap.manifestHash, actual.bootstrap.manifestHash)
        assertEquals(expectedBootstrap.capabilityFlags, actual.bootstrap.capabilityFlags)
        assertArrayEquals(expectedBootstrap.controlEndpoint, actual.bootstrap.controlEndpoint)
        assertEquals(expectedManifest.fileName, actual.manifest.fileName)
        assertEquals(expectedManifest.fileSize, actual.manifest.fileSize)
        assertArrayEquals(expectedManifest.fileSha256, actual.manifest.fileSha256)
        assertEquals(expectedManifest.sourceSymbolBytes, actual.manifest.sourceSymbolBytes)
        assertTrue(expectedManifest.sourceBlockKValues.contentEquals(actual.manifest.sourceBlockKValues))
    }

    private fun decodeMatrix(matrix: BitMatrix): P1BootstrapQr.ScanResult? {
        val scale = 8
        val width = matrix.width * scale
        val height = matrix.height * scale
        val pixels = IntArray(width * height)
        for (moduleY in 0 until matrix.height) {
            for (moduleX in 0 until matrix.width) {
                val color = if (matrix[moduleX, moduleY]) 0x000000 else 0xFFFFFF
                for (dy in 0 until scale) {
                    val offset = (moduleY * scale + dy) * width + moduleX * scale
                    for (dx in 0 until scale) pixels[offset + dx] = color
                }
            }
        }
        val qrText = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))),
            mapOf(DecodeHintType.PURE_BARCODE to true)
        ).text
        return P1BootstrapQr.decodeQrBytes(qrText.toByteArray(Charsets.ISO_8859_1))
    }

    @Test
    fun `transfer container round trips bootstrap and manifest`() {
        val manifest = manifest()
        val bootstrap = bootstrap(manifest)

        val result = P1BootstrapQr.decodeQrBytes(P1BootstrapQr.encodeTransfer(bootstrap, manifest))

        assertTrue(result is P1BootstrapQr.ScanResult.Transfer)
        assertSameTransfer(bootstrap, manifest, result as P1BootstrapQr.ScanResult.Transfer)
    }

    @Test
    fun `transfer container rejects a manifest whose hash differs from the bootstrap`() {
        val original = manifest()
        val tampered = manifest(fileName = "tampered.bin")
        val bootstrap = bootstrap(original)

        val result = P1BootstrapQr.decodeQrBytes(P1BootstrapQr.encodeTransfer(bootstrap, tampered))

        assertEquals(null, result)
    }

    @Test
    fun `bare bootstrap decodes as bootstrap only`() {
        val bootstrap = bootstrap(manifest())

        val result = P1BootstrapQr.decodeQrBytes(OptiFrameBootstrapCodec.encode(bootstrap))

        assertTrue(result is P1BootstrapQr.ScanResult.BootstrapOnly)
        val decoded = (result as P1BootstrapQr.ScanResult.BootstrapOnly).bootstrap
        assertEquals(bootstrap.sessionId, decoded.sessionId)
        assertEquals(bootstrap.fileSize.toLong(), decoded.fileSize.toLong())
        assertArrayEquals(bootstrap.fileSha256, decoded.fileSha256)
        assertArrayEquals(bootstrap.manifestHash, decoded.manifestHash)
    }

    @Test
    fun `100 MiB transfer round trips through an ECC M QR matrix`() {
        val fileSize = 100L * 1024L * 1024L
        val manifest = manifest(
            fileName = "transfer-${"x".repeat(48)}.bin",
            fileSize = fileSize,
            sourceBlockKValues = IntArray(400) { 1024 }
        )
        val bootstrap = bootstrap(manifest)
        val encoded = P1BootstrapQr.encodeMatrix(bootstrap, manifest)
        val matrix = encoded.matrix
        assertTrue(encoded.includesManifest)
        val result = decodeMatrix(matrix)

        assertNotNull(result)
        assertTrue(result is P1BootstrapQr.ScanResult.Transfer)
        assertSameTransfer(bootstrap, manifest, result as P1BootstrapQr.ScanResult.Transfer)
        println("Transfer QR matrix: ${matrix.width}×${matrix.height} modules")
    }

    @Test
    fun `300 MiB transfer retries ECC L and keeps its manifest`() {
        val fileSize = 300L * 1024L * 1024L
        val manifest = manifest(
            fileName = "transfer-${"x".repeat(48)}.bin",
            fileSize = fileSize,
            sourceBlockKValues = IntArray(1200) { 1024 }
        )
        val bootstrap = bootstrap(manifest)
        val encoded = P1BootstrapQr.encodeMatrix(bootstrap, manifest)

        assertTrue(encoded.includesManifest)
        val result = decodeMatrix(encoded.matrix)

        assertTrue(result is P1BootstrapQr.ScanResult.Transfer)
        assertSameTransfer(bootstrap, manifest, result as P1BootstrapQr.ScanResult.Transfer)
    }

    @Test
    fun `300 MiB and 1 GiB manifests fall back to bootstrap-only QRs when neither ECC level fits`() {
        val manifests = listOf(
            manifest(
                fileName = "transfer-${"x".repeat(48)}.bin",
                fileSize = 300L * 1024L * 1024L,
                sourceBlockKValues = IntArray(1536) { 800 }
            ),
            manifest(
                fileName = "transfer-${"x".repeat(48)}.bin",
                fileSize = 1024L * 1024L * 1024L,
                sourceBlockKValues = IntArray(4096) { 1024 }
            )
        )

        manifests.forEach { manifest ->
            val bootstrap = bootstrap(manifest)
            val encoded = P1BootstrapQr.encodeMatrix(bootstrap, manifest)

            assertTrue(!encoded.includesManifest)
            val result = try {
                decodeMatrix(encoded.matrix)
            } catch (error: Exception) {
                throw AssertionError(
                    "Could not decode fallback QR for ${manifest.fileSize} bytes " +
                        "with ${manifest.sourceBlockCount} blocks (${encoded.matrix.width} modules)",
                    error
                )
            }

            assertTrue(result is P1BootstrapQr.ScanResult.BootstrapOnly)
            val decoded = (result as P1BootstrapQr.ScanResult.BootstrapOnly).bootstrap
            assertEquals(bootstrap.sessionId, decoded.sessionId)
            assertEquals(bootstrap.fileSize, decoded.fileSize)
            assertArrayEquals(bootstrap.fileSha256, decoded.fileSha256)
            assertArrayEquals(bootstrap.manifestHash, decoded.manifestHash)
        }
    }
}
