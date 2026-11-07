package com.kieran.optiframe.ui.optical

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.kieran.optiframe.protocol.OptiFrameBootstrapCodec
import com.kieran.optiframe.protocol.OptiFrameBootstrapPayload

/** Standard QR transport for the existing V1 bootstrap payload. */
object P1BootstrapQr {
    private const val QR_SIZE = 640

    fun render(payload: OptiFrameBootstrapPayload): Bitmap {
        val raw = OptiFrameBootstrapCodec.encode(payload)
        val binaryText = String(raw, Charsets.ISO_8859_1)
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "ISO-8859-1")
        val matrix = QRCodeWriter().encode(binaryText, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
        val pixels = IntArray(QR_SIZE * QR_SIZE)
        for (y in 0 until QR_SIZE) for (x in 0 until QR_SIZE) {
            pixels[y * QR_SIZE + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Bitmap.createBitmap(QR_SIZE, QR_SIZE, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, QR_SIZE, 0, 0, QR_SIZE, QR_SIZE)
        }
    }

    /** Called while scanning bootstrap only; avoids QR work during optical frame reception. */
    fun scan(image: ImageProxy): OptiFrameBootstrapPayload? {
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
            val bytes = text.toByteArray(Charsets.ISO_8859_1)
            OptiFrameBootstrapCodec.decode(bytes)
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
