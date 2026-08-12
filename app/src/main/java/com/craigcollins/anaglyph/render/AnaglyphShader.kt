package com.craigcollins.anaglyph.render

/**
 * OpenGL ES 2.0 fragment shader source for the anaglyph rendering pipeline.
 *
 * This shader performs:
 * 1. Depth-based horizontal pixel displacement to synthesize left/right eye views
 * 2. Red-cyan anaglyph compositing
 * 3. Edge fade using a dedicated mask's red channel (NOT the anaglyph's red)
 *
 * The shader is provided as source strings ready to be compiled and linked
 * in a GLSurfaceView or similar OpenGL ES context. The current CPU fallback
 * renderer ([StereoRenderer]) implements the same math on the CPU for
 * compatibility without a GL surface.
 *
 * Uniforms:
 * - uPhoto:    sampler2D — the original source image
 * - uDepth:   sampler2D — the normalized depth map (grayscale, R channel)
 * - uFadeMask: sampler2D — dedicated edge fade mask (opacity in R channel)
 * - uFocusDepth: float — focal plane depth [0, 1]
 * - uMaxParallax: float — maximum horizontal shift in pixels
 * - uDepthStrength: float — user depth strength multiplier
 * - uEyeSwap: int — 0 = normal, 1 = swap left/right
 * - uEdgeFade: float — edge fade strength [0, 1]
 */
object AnaglyphShader {

    /** Vertex shader — standard full-screen quad. */
    val vertexShader = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;

        varying vec2 vTexCoord;

        void main() {
            vTexCoord = aTexCoord;
            gl_Position = aPosition;
        }
    """.trimIndent()

    /**
     * Fragment shader — depth warp, stereo synthesis, red-cyan composite, edge fade.
     *
     * Key formula:
     *   offset = (depth - focusDepth) * maxParallax * depthStrength
     *   leftEye  = sample(uPhoto, uv + offset * 0.5, 0)
     *   rightEye = sample(uPhoto, uv - offset * 0.5, 0)
     *   anaglyph = vec3(leftEye.r, rightEye.g, rightEye.b)
     *   alpha = texture(uFadeMask, uv).r  (dedicated mask, NOT anaglyph R)
     */
    val fragmentShader = """
        precision mediump float;

        uniform sampler2D uPhoto;
        uniform sampler2D uDepth;
        uniform sampler2D uFadeMask;
        uniform float uFocusDepth;
        uniform float uMaxParallax;
        uniform float uDepthStrength;
        uniform int uEyeSwap;
        uniform float uEdgeFade;

        varying vec2 vTexCoord;

        void main() {
            // Sample depth (normalized 0-1, nearer = larger)
            float d = texture2D(uDepth, vTexCoord).r;

            // Compute horizontal displacement
            float offset = (d - uFocusDepth) * uMaxParallax * uDepthStrength;
            float halfOffset = offset * 0.5;

            // Convert pixel offset to UV offset
            // (assumes texture is width pixels; caller sets 1.0/width)
            vec2 texelSize = vec2(1.0) / vec2(textureSize2D(uPhoto, 0));

            // Backward sample for left and right eye views
            vec3 leftEye  = texture2D(uPhoto, vTexCoord + vec2(halfOffset, 0.0) * texelSize).rgb;
            vec3 rightEye = texture2D(uPhoto, vTexCoord - vec2(halfOffset, 0.0) * texelSize).rgb;

            vec3 anaglyph;
            if (uEyeSwap == 1) {
                // Swap: right eye → red, left eye → green+blue
                anaglyph = vec3(rightEye.r, leftEye.g, leftEye.b);
            } else {
                // Normal: left eye → red, right eye → green+blue
                anaglyph = vec3(leftEye.r, rightEye.g, rightEye.b);
            }

            // Edge fade: use dedicated mask's RED channel only
            // Do NOT derive fade from the anaglyph's RGB channels
            float edgeOpacity = texture2D(uFadeMask, vTexCoord).r;
            float alpha = mix(1.0, edgeOpacity, uEdgeFade);

            gl_FragColor = vec4(anaglyph, alpha);
        }
    """.trimIndent()
}
