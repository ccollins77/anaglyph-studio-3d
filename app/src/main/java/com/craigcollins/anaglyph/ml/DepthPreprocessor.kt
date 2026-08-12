package com.craigcollins.anaglyph.ml

import android.graphics.Bitmap
import com.craigcollins.anaglyph.domain.DepthModelSpec

/**
 * Preprocesses a source [Bitmap] into the float tensor format
 * expected by the depth estimation model.
 *
 * Output is NHWC layout [1, inputSize, inputSize, numChannels]
 * with values normalized to [0.0, 1.0].
 */
class DepthPreprocessor(private val spec: DepthModelSpec = DepthModelSpec()) {

    /**
     * Resize the bitmap to the model's input size and convert to a
     * normalized float array in NHWC layout.
     *
     * @param bitmap Source image
     * @return FloatArray of length [inputSize * inputSize * numChannels]
     */
    fun preprocess(bitmap: Bitmap): FloatArray {
        val size = spec.inputSize
        val resized = if (bitmap.width == size && bitmap.height == size) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        }

        val pixels = IntArray(size * size)
        resized.getPixels(pixels, 0, size, 0, 0, size, size)

        val output = FloatArray(size * size * spec.numChannels)
        var idx = 0
        for (pixel in pixels) {
            output[idx++] = ((pixel shr 16) and 0xFF) / 255.0f  // R
            if (spec.numChannels >= 2) {
                output[idx++] = ((pixel shr 8) and 0xFF) / 255.0f  // G
            }
            if (spec.numChannels >= 3) {
                output[idx++] = (pixel and 0xFF) / 255.0f  // B
            }
        }

        return output
    }

    /**
     * Get the model's input dimension (square).
     */
    val inputSize: Int get() = spec.inputSize
}
