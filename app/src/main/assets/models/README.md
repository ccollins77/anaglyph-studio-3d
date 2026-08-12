# Depth Model

Place a licensed monocular depth estimation `.tflite` model here as `depth_model.tflite`.

## Requirements

- Input: `[1, 384, 384, 3]` float32 RGB, normalized to [0, 1]
- Output: `[1, 384, 384, 1]` float32 depth map
- Convention: larger output value = nearer (configurable in `DepthModelSpec`)

The app will run with GPU acceleration first (LiteRT `Accelerator.GPU`),
falling back to CPU if GPU is unavailable.

If no model is present, the app uses a synthetic depth fallback (center-weighted
gradient) so the single-image anaglyph workflow remains functional.
