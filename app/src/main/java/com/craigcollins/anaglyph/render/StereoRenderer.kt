package com.craigcollins.anaglyph.render

import android.graphics.Bitmap
import android.graphics.Color
import com.craigcollins.anaglyph.domain.EditorSettings
import kotlin.math.roundToInt

/**
 * CPU-based stereo renderer that generates a red-cyan anaglyph from a
 * single source image and its depth map.
 *
 * The renderer performs depth-based horizontal pixel displacement to
 * synthesize left and right eye views, then composites them into a
 * red-cyan anaglyph. Small disocclusion gaps are handled by clamping
 * to the nearest valid source pixel (backward sampling).
 *
 * The OpenGL ES shader equivalents are defined in [AnaglyphShader]
 * for future GPU-accelerated rendering.
 */
class StereoRenderer {

    /**
     * Render an anaglyph bitmap from a source image and depth map.
     *
     * @param source   Original source image
     * @param depth    Normalized depth values [0, 1], sized [width * height] matching source
     * @param settings Editor settings controlling the stereo effect
     * @param fadeMask Optional edge fade mask (R channel used for alpha), same dimensions as source
     * @return the composited red-cyan anaglyph [Bitmap]
     */
    fun render(
        source: Bitmap,
        depth: FloatArray,
        settings: EditorSettings,
        fadeMask: Bitmap? = null,
    ): Bitmap {
        val width = source.width
        val height = source.height
        require(depth.size == width * height) {
            "Depth array size ${depth.size} doesn't match image ${width}x${height}"
        }

        val srcPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)

        val maxParallax = settings.maxParallaxPx.toFloat()
        val strength = settings.depthStrength.coerceIn(0f, 2f)
        val focus = settings.focusDepth.coerceIn(0f, 1f)
        val eyeSwap = settings.eyeSwap

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val d = depth[idx]

                // Depth-based horizontal displacement:
                // Δx = maxParallax * (depth - focusDepth) * depthStrength
                val offset = (d - focus) * maxParallax * strength

                // Backward sample: for the left eye, shift right; for right eye, shift left
                var leftX = x + (offset * 0.5f).roundToInt()
                var rightX = x - (offset * 0.5f).roundToInt()

                // Clamp to valid range (simple hole fill — nearest valid pixel)
                leftX = leftX.coerceIn(0, width - 1)
                rightX = rightX.coerceIn(0, width - 1)

                val leftPixel = srcPixels[y * width + leftX]
                val rightPixel = srcPixels[y * width + rightX]

                // Extract channels
                val lr = (leftPixel shr 16) and 0xFF
                val lg = (leftPixel shr 8) and 0xFF
                val lb = leftPixel and 0xFF

                val rr = (rightPixel shr 16) and 0xFF
                val rg = (rightPixel shr 8) and 0xFF
                val rb = rightPixel and 0xFF

                // Red-cyan anaglyph: left eye → red, right eye → green + blue
                val r: Int
                val g: Int
                val b: Int
                if (eyeSwap) {
                    // Swap eyes: right → red, left → green + blue
                    r = rr
                    g = lg
                    b = lb
                } else {
                    r = lr
                    g = rg
                    b = rb
                }

                // Edge fade: use the fade mask's RED channel only (not the anaglyph's R)
                val baseAlpha = 255
                val fadeAlpha = if (fadeMask != null) {
                    val fadePixel = fadeMask.getPixel(x, y)
                    ((fadePixel shr 16) and 0xFF) / 255f
                } else {
                    1.0f - settings.edgeFade
                }
                val alpha = (baseAlpha * fadeAlpha).roundToInt().coerceIn(0, 255)

                outPixels[idx] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }
}
