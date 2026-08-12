package com.craigcollins.anaglyph.domain

/**
 * User-adjustable editor settings that control the anaglyph rendering pipeline.
 *
 * @param depthStrength Multiplier for the stereo parallax effect (0.0–2.0, default 1.0)
 * @param focusDepth    Depth value at the focal plane (0.0–1.0, default 0.5).
 *                      Pixels at this depth remain centered; nearer/farther pixels shift.
 * @param eyeSwap       If true, swap left/right eye channels (inverts perceived depth direction)
 * @param edgeFade      Edge fade strength (0.0 = no fade, 1.0 = maximum fade)
 * @param maxParallaxPx Maximum horizontal pixel shift for the stereo effect
 */
data class EditorSettings(
    val depthStrength: Float = 1.0f,
    val focusDepth: Float = 0.5f,
    val eyeSwap: Boolean = false,
    val edgeFade: Float = 0.3f,
    val maxParallaxPx: Int = 20,
)
