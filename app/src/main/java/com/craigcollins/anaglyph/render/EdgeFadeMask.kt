package com.craigcollins.anaglyph.render

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.hypot

/**
 * Generates a dedicated edge-fade mask independent from the anaglyph colors.
 *
 * Per the 3D Edge Fade specification:
 * - Opacity is derived from the mask's RED channel only
 * - The fade mask must NOT be derived from the final anaglyph's RGB channels
 *   (which contain stereoscopic color encoding, not neutral mask intensity)
 *
 * The mask is a radial gradient: fully opaque in the center, fading to
 * transparent at the edges. This creates a vignette-like fade that prevents
 * stereo artifacts at image borders where depth-based shifting would
 * expose empty pixels.
 */
object EdgeFadeMask {

    /**
     * Create a radial edge-fade mask bitmap.
     *
     * The mask stores its opacity value in the RED channel.
     * R = 0 → fully transparent, R = 255 → fully opaque.
     *
     * @param width    Mask width in pixels
     * @param height   Mask height in pixels
     * @param strength Fade strength [0, 1] — 0 = no fade, 1 = maximum vignette
     * @return a [Bitmap] with opacity encoded in the R channel
     */
    fun createMask(width: Int, height: Int, strength: Float): Bitmap {
        val pixels = IntArray(width * height)
        val centerX = width / 2f
        val centerY = height / 2f
        val maxDist = hypot(centerX, centerY)

        val s = strength.coerceIn(0f, 1f)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val dist = hypot(dx, dy)
                val normalizedDist = (dist / maxDist).coerceIn(0f, 1f)

                // Smooth radial falloff (cosine curve for gentle transition)
                val falloff = (0.5f * (1f + kotlin.math.cos(normalizedDist * Math.PI.toFloat()))).coerceIn(0f, 1f)

                // Blend between fully opaque (1.0) and the falloff curve
                val opacity = (1.0f - s + s * falloff).coerceIn(0f, 1f)
                val gray = (opacity * 255).toInt().coerceIn(0, 255)

                // Store in R channel; duplicate to G and B for visual inspection
                pixels[y * width + x] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Query the alpha value at a specific pixel from a fade mask.
     *
     * @param mask   The fade mask bitmap
     * @param x      X coordinate
     * @param y      Y coordinate
     * @return opacity [0, 1] derived from the mask's R channel
     */
    fun alphaAt(mask: Bitmap, x: Int, y: Int): Float {
        val pixel = mask.getPixel(x, y)
        return ((pixel shr 16) and 0xFF) / 255f
    }
}
