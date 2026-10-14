package com.example.util

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Universal, high-performance bitmap blur transformation compatible with Android API 24+.
 * Uses fast downsampling + StackBlur to produce a silky, reliable blur on all Android versions
 * (including Android 11 and below where Compose Modifier.blur / RenderEffect is unavailable).
 */
class ArtfluxBlurTransformation(
    private val radius: Int = 20,
    private val sampling: Float = 4f
) : Transformation {

    override val cacheKey: String = "ArtfluxBlurTransformation(radius=$radius,sampling=$sampling)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width <= 0 || height <= 0) return input

        val scaledWidth = (width / sampling).toInt().coerceAtLeast(1)
        val scaledHeight = (height / sampling).toInt().coerceAtLeast(1)

        val scaledBitmap = Bitmap.createScaledBitmap(input, scaledWidth, scaledHeight, false)
        val blurredBitmap = fastBlur(scaledBitmap, radius.coerceIn(1, 25))

        val output = Bitmap.createScaledBitmap(blurredBitmap, width, height, true)
        if (scaledBitmap != input && scaledBitmap != blurredBitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }
        if (blurredBitmap != output && !blurredBitmap.isRecycled) {
            blurredBitmap.recycle()
        }
        return output
    }

    companion object {
        /**
         * Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>
         * Fast, thread-safe, and works across all Android API levels without RenderScript or API 31+.
         */
        fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
            val bitmap = sentBitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (radius < 1) return bitmap

            val w = bitmap.width
            val h = bitmap.height
            val pix = IntArray(w * h)
            bitmap.getPixels(pix, 0, w, 0, 0, w, h)

            val wm = w - 1
            val hm = h - 1
            val wh = w * h
            val div = radius + radius + 1

            val r = IntArray(wh)
            val g = IntArray(wh)
            val b = IntArray(wh)
            var rsum: Int
            var gsum: Int
            var bsum: Int
            var p: Int
            var yp: Int
            var yi: Int
            var yw: Int
            val vmin = IntArray(max(w, h))

            var divsum = (div + 1) shr 1
            divsum *= divsum
            val dv = IntArray(256 * divsum)
            for (j in 0 until 256 * divsum) {
                dv[j] = j / divsum
            }

            yw = 0
            yi = 0

            val stack = Array(div) { IntArray(3) }
            var stackpointer: Int
            var stackstart: Int
            var routsum: Int
            var goutsum: Int
            var boutsum: Int
            var rinsum: Int
            var ginsum: Int
            var binsum: Int

            for (yIdx in 0 until h) {
                rinsum = 0
                ginsum = 0
                binsum = 0
                routsum = 0
                goutsum = 0
                boutsum = 0
                rsum = 0
                gsum = 0
                bsum = 0
                for (k in -radius..radius) {
                    p = pix[yi + min(wm, max(k, 0))]
                    val sir = stack[k + radius]
                    sir[0] = (p and 0xff0000) shr 16
                    sir[1] = (p and 0x00ff00) shr 8
                    sir[2] = p and 0x0000ff
                    val rbsVal = radius + 1 - abs(k)
                    rsum += sir[0] * rbsVal
                    gsum += sir[1] * rbsVal
                    bsum += sir[2] * rbsVal
                    if (k > 0) {
                        rinsum += sir[0]
                        ginsum += sir[1]
                        binsum += sir[2]
                    } else {
                        routsum += sir[0]
                        goutsum += sir[1]
                        boutsum += sir[2]
                    }
                }
                stackpointer = radius

                for (xIdx in 0 until w) {
                    r[yi] = dv[rsum]
                    g[yi] = dv[gsum]
                    b[yi] = dv[bsum]

                    rsum -= routsum
                    gsum -= goutsum
                    bsum -= boutsum

                    stackstart = stackpointer - radius + div
                    val sir = stack[stackstart % div]

                    routsum -= sir[0]
                    goutsum -= sir[1]
                    boutsum -= sir[2]

                    if (yIdx == 0) {
                        vmin[xIdx] = min(xIdx + radius + 1, wm)
                    }
                    p = pix[yw + vmin[xIdx]]

                    sir[0] = (p and 0xff0000) shr 16
                    sir[1] = (p and 0x00ff00) shr 8
                    sir[2] = p and 0x0000ff

                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]

                    rsum += rinsum
                    gsum += ginsum
                    bsum += binsum

                    stackpointer = (stackpointer + 1) % div
                    val sirOut = stack[stackpointer % div]

                    routsum += sirOut[0]
                    goutsum += sirOut[1]
                    boutsum += sirOut[2]

                    rinsum -= sirOut[0]
                    ginsum -= sirOut[1]
                    binsum -= sirOut[2]

                    yi++
                }
                yw += w
            }

            for (xIdx in 0 until w) {
                rinsum = 0
                ginsum = 0
                binsum = 0
                routsum = 0
                goutsum = 0
                boutsum = 0
                rsum = 0
                gsum = 0
                bsum = 0
                yp = -radius * w
                for (k in -radius..radius) {
                    yi = max(0, yp) + xIdx
                    val sir = stack[k + radius]
                    sir[0] = r[yi]
                    sir[1] = g[yi]
                    sir[2] = b[yi]
                    val rbsVal = radius + 1 - abs(k)
                    rsum += r[yi] * rbsVal
                    gsum += g[yi] * rbsVal
                    bsum += b[yi] * rbsVal
                    if (k > 0) {
                        rinsum += sir[0]
                        ginsum += sir[1]
                        binsum += sir[2]
                    } else {
                        routsum += sir[0]
                        goutsum += sir[1]
                        boutsum += sir[2]
                    }
                    if (k < hm) {
                        yp += w
                    }
                }
                yi = xIdx
                stackpointer = radius
                for (yIdx in 0 until h) {
                    pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                    rsum -= routsum
                    gsum -= goutsum
                    bsum -= boutsum

                    stackstart = stackpointer - radius + div
                    val sir = stack[stackstart % div]

                    routsum -= sir[0]
                    goutsum -= sir[1]
                    boutsum -= sir[2]

                    if (xIdx == 0) {
                        vmin[yIdx] = min(yIdx + radius + 1, hm) * w
                    }
                    p = xIdx + vmin[yIdx]

                    sir[0] = r[p]
                    sir[1] = g[p]
                    sir[2] = b[p]

                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]

                    rsum += rinsum
                    gsum += ginsum
                    bsum += binsum

                    stackpointer = (stackpointer + 1) % div
                    val sirOut = stack[stackpointer % div]

                    routsum += sirOut[0]
                    goutsum += sirOut[1]
                    boutsum += sirOut[2]

                    rinsum -= sirOut[0]
                    ginsum -= sirOut[1]
                    binsum -= sirOut[2]

                    yi += w
                }
            }

            bitmap.setPixels(pix, 0, w, 0, 0, w, h)
            return bitmap
        }
    }
}
