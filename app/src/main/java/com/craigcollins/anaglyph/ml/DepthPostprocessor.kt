package com.craigcollins.anaglyph.ml

import android.graphics.Bitmap
import com.craigcollins.anaglyph.domain.DepthConvention
import com.craigcollins.anaglyph.domain.DepthModelSpec

/**
 * Postprocesses raw model output into a normalized depth map
 * suitable for stereo rendering and UI preview.
 *
 * Responsibilities:
 * - Normalize raw output to [0.0, 1.0]
 * - Invert if the model convention is LARGER_IS_FAR
 * - Reshape the flat array to a 2D grid
 * - Scale up to the source image dimensions
 * - Produce a grayscale [Bitmap] for UI display
 */
class DepthPostprocessor(private val spec: DepthModelSpec = DepthModelSpec()) {

    /**
     * Process raw model output into a normalized depth FloatArray.
     *
     * @param rawOutput Flat float array from the model
     * @return normalized depth values in [0, 1], shaped [outputSize * outputSize]
     */
    fun normalize(rawOutput: FloatArray): FloatArray {
        if (rawOutput.isEmpty()) return FloatArray(0)

        // Find min/max for normalization
        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE
        for (v in rawOutput) {
            if (v.isFinite()) {
                if (v < min) min = v
                if (v > max) max = v
            }
        }
        val range = (max - min).coerceAtLeast(1e-6f)

        // Normalize to [0, 1]
        var normalized = FloatArray(rawOutput.size) { i ->
            ((rawOutput[i] - min) / range).coerceIn(0f, 1f)
        }

        // Invert if the model convention says larger = farther
        if (spec.depthConvention == DepthConvention.LARGER_IS_FAR) {
            for (i in normalized.indices) {
                normalized[i] = 1.0f - normalized[i]
            }
        }

        return normalized
    }

    /**
     * Create a grayscale depth map [Bitmap] from normalized depth values.
     *
     * @param normalizedDepth FloatArray in [0, 1], shaped [size * size]
     * @param size           Side length of the square depth map
     * @return grayscale [Bitmap] of size [size] × [size]
     */
    fun toBitmap(normalizedDepth: FloatArray, size: Int): Bitmap {
        val pixels = IntArray(normalizedDepth.size)
        for (i in normalizedDepth.indices) {
            val gray = (normalizedDepth[i] * 255).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Upscale a depth map to the target dimensions using nearest-neighbor
     * for speed (preview quality is sufficient).
     *
     * @param depth     Normalized depth values [srcSize * srcSize]
     * @param srcSize   Source square side length
     * @param dstWidth  Target width
     * @param dstHeight Target height
     * @return upscaled depth values [dstWidth * dstHeight]
     */
    fun upscale(
        depth: FloatArray,
        srcSize: Int,
        dstWidth: Int,
        dstHeight: Int
    ): FloatArray {
        val output = FloatArray(dstWidth * dstHeight)
        val xRatio = srcSize.toFloat() / dstWidth
        val yRatio = srcSize.toFloat() / dstHeight

        for (y in 0 until dstHeight) {
            val srcY = (y * yRatio).toInt().coerceIn(0, srcSize - 1)
            for (x in 0 until dstWidth) {
                val srcX = (x * xRatio).toInt().coerceIn(0, srcSize - 1)
                output[y * dstWidth + x] = depth[srcY * srcSize + srcX]
            }
        }
        return output
    }
}
