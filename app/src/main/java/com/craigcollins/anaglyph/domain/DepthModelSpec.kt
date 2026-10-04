package com.craigcollins.anaglyph.domain

/**
 * Specification for the monocular depth estimation model.
 *
 * This abstraction allows swapping the depth model without rewriting the
 * renderer or estimator logic. The default values target a 384×384 input
 * model in NHWC layout with float32 RGB normalized to [0, 1].
 *
 * @param assetPath    Path to the .tflite model within the app's assets
 * @param inputSize    Square input dimension (e.g. 384 for the Balanced profile)
 * @param numChannels  Number of color channels (3 for RGB)
 * @param outputSize   Square output dimension (typically matches inputSize)
 * @param depthConvention Whether larger output values mean nearer or farther
 */
data class DepthModelSpec(
    val assetPath: String = "models/midas_small_256_fp16.tflite",
    val inputSize: Int = 384,
    val numChannels: Int = 3,
    val outputSize: Int = 384,
    val depthConvention: DepthConvention = DepthConvention.LARGER_IS_NEAR,
)

/**
 * Depth output convention from the model.
 */
enum class DepthConvention {
    /** Larger output value = closer to camera. */
    LARGER_IS_NEAR,

    /** Larger output value = farther from camera. */
    LARGER_IS_FAR,
}
