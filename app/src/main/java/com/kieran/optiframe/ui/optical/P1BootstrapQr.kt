package com.kieran.optiframe.ui.optical

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kieran.optiframe.protocol.OptiFrameBootstrapCodec
import com.kieran.optiframe.protocol.OptiFrameBootstrapPayload
import com.kieran.optiframe.protocol.OptiFrameManifest
import com.kieran.optiframe.protocol.OptiFrameManifestCodec

/** QR transport for either a standalone V1 bootstrap or a bootstrap and manifest transfer. */
object P1BootstrapQr {
    private val TRANSFER_MAGIC = "OFQ1".encodeToByteArray()
    private const val MINIMUM_BITMAP_SIZE = 1000

    sealed interface ScanResult {
        data class Transfer(
            val bootstrap: OptiFrameBootstrapPayload,
            val manifest: OptiFrameManifest
        ) : ScanResult

        data class BootstrapOnly(val bootstrap: OptiFrameBootstrapPayload) : ScanResult
    }

    fun encodeTransfer(
        bootstrap: OptiFrameBootstrapPayload,
        manifest: OptiFrameManifest
    ): ByteArray {
        val bootstrapBytes = OptiFrameBootstrapCodec.encode(bootstrap)
        require(bootstrapBytes.size <= 0xFFFF) { "Bootstrap payload is too long for a transfer QR" }
        val manifestBytes = OptiFrameManifestCodec.encode(manifest)
        val encoded = ByteArray(TRANSFER_MAGIC.size + 2 + bootstrapBytes.size + manifestBytes.size)
        TRANSFER_MAGIC.copyInto(encoded)
        val lengthOffset = TRANSFER_MAGIC.size
        encoded[lengthOffset] = (bootstrapBytes.size ushr 8).toByte()
        encoded[lengthOffset + 1] = bootstrapBytes.size.toByte()
        bootstrapBytes.copyInto(encoded, lengthOffset + 2)
        manifestBytes.copyInto(encoded, lengthOffset + 2 + bootstrapBytes.size)
        return encoded
    }

    fun decodeQrBytes(bytes: ByteArray): ScanResult? {
        val hasTransferMagic = bytes.size >= TRANSFER_MAGIC.size &&
            TRANSFER_MAGIC.indices.all { bytes[it] == TRANSFER_MAGIC[it] }
        if (!hasTransferMagic) {
            val bootstrap = runCatching { OptiFrameBootstrapCodec.decode(bytes) }.getOrNull()
                ?: return null
            return ScanResult.BootstrapOnly(bootstrap)
        }
        if (bytes.size < TRANSFER_MAGIC.size + 2) return null

        val lengthOffset = TRANSFER_MAGIC.size
        val bootstrapLength = ((bytes[lengthOffset].toInt() and 0xFF) shl 8) or
            (bytes[lengthOffset + 1].toInt() and 0xFF)
        val manifestOffset = lengthOffset + 2 + bootstrapLength
        if (bootstrapLength == 0 || manifestOffset >= bytes.size) return null

        val decoded = runCatching {
            val bootstrap = OptiFrameBootstrapCodec.decode(
                bytes.copyOfRange(lengthOffset + 2, manifestOffset)
            )
            val manifest = OptiFrameManifestCodec.decode(bytes.copyOfRange(manifestOffset, bytes.size))
            if (bootstrap.manifestHash.contentEquals(OptiFrameManifestCodec.hash(manifest)) &&
                bootstrap.fileSize.toLong() == manifest.fileSize &&
                bootstrap.fileSha256.contentEquals(manifest.fileSha256)
            ) {
                ScanResult.Transfer(bootstrap, manifest)
            } else {
                null
            }
        }.getOrNull()
        return decoded
    }

    /** Builds the minimal QR module matrix, including its four-module quiet zone. */
    internal fun encodeMatrix(
        bootstrap: OptiFrameBootstrapPayload,
        manifest: OptiFrameManifest
    ): BitMatrix {
        val binaryText = String(encodeTransfer(bootstrap, manifest), Charsets.ISO_8859_1)
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4
        )
        return QRCodeWriter().encode(binaryText, BarcodeFormat.QR_CODE, 0, 0, hints)
    }

    fun render(bootstrap: OptiFrameBootstrapPayload, manifest: OptiFrameManifest): Bitmap {
        val matrix = encodeMatrix(bootstrap, manifest)
        val modules = maxOf(matrix.width, matrix.height)
        val scale = maxOf(1, (MINIMUM_BITMAP_SIZE + modules - 1) / modules)
        val width = matrix.width * scale
        val height = matrix.height * scale
        val pixels = IntArray(width * height)
        for (moduleY in 0 until matrix.height) {
            for (moduleX in 0 until matrix.width) {
                val color = if (matrix[moduleX, moduleY]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                for (dy in 0 until scale) {
                    val offset = (moduleY * scale + dy) * width + moduleX * scale
                    for (dx in 0 until scale) pixels[offset + dx] = color
                }
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /** Called while scanning setup QRs; avoids QR work during optical frame reception. */
    fun scan(image: ImageProxy): ScanResult? {
        val source = when {
            image.planes.size == 3 -> {
                val yPlane = image.planes[0]
                val buffer = yPlane.buffer.duplicate()
                val luma = ByteArray(image.width * image.height)
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val offset = y * yPlane.rowStride + x * yPlane.pixelStride
                    if (offset >= buffer.limit()) return null
                    luma[y * image.width + x] = buffer.get(offset)
                }
                PlanarYUVLuminanceSource(
                    luma, image.width, image.height,
                    0, 0, image.width, image.height, false
                )
            }
            image.planes.size == 1 -> {
                val plane = image.planes[0]
                val buffer = plane.buffer.duplicate()
                val rgb = IntArray(image.width * image.height)
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val offset = y * plane.rowStride + x * plane.pixelStride
                    if (offset + 2 >= buffer.limit()) return null
                    val red = buffer.get(offset).toInt() and 0xFF
                    val green = buffer.get(offset + 1).toInt() and 0xFF
                    val blue = buffer.get(offset + 2).toInt() and 0xFF
                    rgb[y * image.width + x] = (red shl 16) or (green shl 8) or blue
                }
                RGBLuminanceSource(image.width, image.height, rgb)
            }
            else -> return null
        }
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
        return try {
            val text = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            decodeQrBytes(text.toByteArray(Charsets.ISO_8859_1))
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
