package com.kieran.optiframe

import com.kieran.optiframe.ui.optical.P1PerspectiveSampler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class P1PerspectiveSamplerTest {

    private fun plane(vararg rows: IntArray, pixelStride: Int = 1): P1PerspectiveSampler.PlaneView {
        val height = rows.size
        val width = rows[0].size
        val data = ByteArray(width * height * pixelStride)
        for (y in 0 until height) for (x in 0 until width) {
            data[y * width * pixelStride + x * pixelStride] = rows[y][x].toByte()
        }
        return P1PerspectiveSampler.PlaneView(data, width * pixelStride, pixelStride, width, height)
    }

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
}
