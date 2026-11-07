package com.kieran.optiframe

import androidx.camera.core.ImageProxy
import com.kieran.optiframe.ui.optical.P1PerspectiveSampler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

class P1PerspectiveSamplerTest {
    private data class PlaneData(
        val bytes: ByteArray,
        val rowStride: Int,
        val pixelStride: Int
    )

    private fun plane(vararg rows: IntArray, pixelStride: Int = 1): P1PerspectiveSampler.PlaneView {
        val height = rows.size
        val width = rows[0].size
        val data = ByteArray(width * height * pixelStride)
        for (y in 0 until height) for (x in 0 until width) {
            data[y * width * pixelStride + x * pixelStride] = rows[y][x].toByte()
        }
        return P1PerspectiveSampler.PlaneView(data, width * pixelStride, pixelStride, width, height)
    }

    private fun cameraImage(
        width: Int,
        height: Int,
        vararg planes: PlaneData
    ): ImageProxy {
        val proxyPlanes = planes.map { data ->
            Proxy.newProxyInstance(
                ImageProxy.PlaneProxy::class.java.classLoader,
                arrayOf(ImageProxy.PlaneProxy::class.java)
            ) { proxy, method, arguments ->
                when (method.name) {
                    "getBuffer" -> ByteBuffer.wrap(data.bytes)
                    "getRowStride" -> data.rowStride
                    "getPixelStride" -> data.pixelStride
                    "toString" -> "TestPlaneProxy"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === arguments?.getOrNull(0)
                    else -> null
                }
            } as ImageProxy.PlaneProxy
        }.toTypedArray()
        return Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "getWidth" -> width
                "getHeight" -> height
                "getPlanes" -> proxyPlanes
                "toString" -> "TestImageProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === arguments?.getOrNull(0)
                else -> null
            }
        } as ImageProxy
    }

    private fun yuvImage(width: Int = 160, height: Int = 96): ImageProxy {
        val luma = ByteArray(width * height) { (it % width).toByte() }
        val chroma = ByteArray(width * height / 4) { 128.toByte() }
        return cameraImage(
            width,
            height,
            PlaneData(luma, width, 1),
            PlaneData(chroma, width / 2, 1),
            PlaneData(chroma.clone(), width / 2, 1)
        )
    }

    private fun rgbaImage(width: Int = 160, height: Int = 96): ImageProxy {
        val data = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val offset = (y * width + x) * 4
            data[offset] = x.toByte()
            data[offset + 3] = 0xFF.toByte()
        }
        return cameraImage(width, height, PlaneData(data, width * 4, 4))
    }

    private fun anchors(offsetX: Double = 0.0) = P1PerspectiveSampler.AnchorPoints(
        8.0 + offsetX, 8.0,
        152.0 + offsetX, 8.0,
        152.0 + offsetX, 88.0,
        8.0 + offsetX, 88.0
    )

    @Test
    fun `bilinear returns exact value at integer coordinates`() {
        val p = plane(intArrayOf(10, 20), intArrayOf(30, 40))
        assertEquals(20.0, P1PerspectiveSampler.bilinear(p, 1.0, 0.0), 1e-9)
        assertEquals(30.0, P1PerspectiveSampler.bilinear(p, 0.0, 1.0), 1e-9)
    }

    @Test
    fun `bilinear interpolates between pixels`() {
        val p = plane(intArrayOf(0, 100), intArrayOf(0, 0))
        assertEquals(50.0, P1PerspectiveSampler.bilinear(p, 0.5, 0.0), 1e-9)
        val p2 = plane(intArrayOf(0, 0), intArrayOf(100, 0))
        assertEquals(50.0, P1PerspectiveSampler.bilinear(p2, 0.0, 0.5), 1e-9)
    }

    @Test
    fun `bilinear respects pixel strides`() {
        val p = plane(intArrayOf(0, 50), intArrayOf(0, 0), pixelStride = 2)
        assertEquals(50.0, P1PerspectiveSampler.bilinear(p, 1.0, 0.0), 1e-9)
    }

    @Test
    fun `bilinear clamps out-of-bounds coordinates to the border`() {
        val p = plane(intArrayOf(7, 8), intArrayOf(9, 10))
        assertEquals(7.0, P1PerspectiveSampler.bilinear(p, -5.0, 0.0), 1e-9)
        assertEquals(10.0, P1PerspectiveSampler.bilinear(p, 99.0, 99.0), 1e-9)
    }

    @Test
    fun `map applies the identity homography`() {
        val h = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0)
        val (x, y) = P1PerspectiveSampler.map(h, 12.5, 34.25)
        assertEquals(12.5, x, 1e-9)
        assertEquals(34.25, y, 1e-9)
    }

    @Test
    fun `writeRgb converts neutral YCbCr to grey`() {
        val out = ByteArray(3)
        P1PerspectiveSampler.writeRgb(out, 0, 128.0, 128.0, 128.0)
        assertArrayEquals(byteArrayOf(128.toByte(), 128.toByte(), 128.toByte()), out)
    }

    @Test
    fun `writeRgb converts saturated red`() {
        val out = ByteArray(3)
        // JPEG-range (BT.601 full-swing) red: Y=76, Cb=85, Cr=255
        val offset = P1PerspectiveSampler.writeRgb(out, 0, 76.0, 85.0, 255.0)
        assertEquals(3, offset)
        assertEquals(255.0, (out[0].toInt() and 0xFF).toDouble(), 1.0)
        assertEquals(0.0, (out[1].toInt() and 0xFF).toDouble(), 1.0)
        assertEquals(0.0, (out[2].toInt() and 0xFF).toDouble(), 1.0)
    }

    @Test
    fun `sampling tolerates two out of frame YUV columns and preserves interior pixels`() {
        val sampled = P1PerspectiveSampler.sample(yuvImage(), anchors(offsetX = -2.0))

        assertEquals(2 * 96, P1PerspectiveSampler.lastOutOfImageCells)
        val offset = (20 * 160 + 5) * 3
        assertEquals(3, sampled[offset].toInt() and 0xFF)
        assertEquals(3, sampled[offset + 1].toInt() and 0xFF)
        assertEquals(3, sampled[offset + 2].toInt() and 0xFF)
    }

    @Test
    fun `sampling rejects anchors with more than a quarter of cells out of frame`() {
        var failure: IllegalArgumentException? = null
        try {
            P1PerspectiveSampler.sample(yuvImage(), anchors(offsetX = -65.0))
        } catch (error: IllegalArgumentException) {
            failure = error
        }

        assertNotNull(failure)
        assertTrue(
            failure!!.message!!.startsWith(
                "Anchors map 6240 of 15360 P1 cells outside the camera image; move the receiver back"
            )
        )
        assertEquals(65 * 96, P1PerspectiveSampler.lastOutOfImageCells)
    }

    @Test
    fun `fully in frame YUV anchors report no out of image cells`() {
        P1PerspectiveSampler.sample(yuvImage(), anchors())

        assertEquals(0, P1PerspectiveSampler.lastOutOfImageCells)
    }

    @Test
    fun `RGBA sampling clamps out of frame cells and reports their count`() {
        val sampled = P1PerspectiveSampler.sample(rgbaImage(), anchors(offsetX = -2.0))

        assertEquals(2 * 96, P1PerspectiveSampler.lastOutOfImageCells)
        val offset = (20 * 160 + 5) * 3
        assertEquals(3, sampled[offset].toInt() and 0xFF)
    }
}
