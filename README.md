# Anaglyph Studio 3D

A native Android app that generates 3D red-cyan anaglyph images from a **single** photo using on-device AI depth estimation.

## How It Works

1. **Import** one image via the Android Photo Picker
2. **Depth estimation** — a LiteRT model infers a depth map from the photo (384px Balanced profile)
3. **Stereo synthesis** — the app generates a synthetic second viewpoint via depth-based horizontal pixel displacement
4. **Anaglyph encoding** — left and right eye views are composited into a red-cyan image
5. **Edge fade** — a dedicated mask (R channel only) controls edge transparency
6. **Export** — save the result as PNG/JPEG to Pictures/Anaglyph via MediaStore

If no depth model is bundled, the app uses a synthetic center-weighted depth fallback so the workflow remains functional.

## Architecture

```
com.craigcollins.anaglyph/
├── AnaglyphApplication.kt       App entry point
├── MainActivity.kt              Single Compose activity
├── ui/
│   ├── AnaglyphApp.kt           Root composable + theme
│   ├── EditorScreen.kt          Compose UI with controls
│   ├── EditorViewModel.kt       Pipeline orchestration
│   └── theme/                   Color, Theme, Typography
├── domain/
│   ├── DepthModelSpec.kt        Model configuration abstraction
│   ├── EditorSettings.kt        User-adjustable parameters
│   └── RenderState.kt           Immutable UI state
├── data/
│   ├── BitmapLoader.kt          EXIF-aware image decoding
│   └── MediaStoreExporter.kt    Export to Pictures/Anaglyph
├── ml/
│   ├── LiteRtDepthEstimator.kt  LiteRT 2.1.0 (GPU-first, CPU fallback)
│   ├── DepthPreprocessor.kt     NHWC tensor preparation
│   └── DepthPostprocessor.kt    Depth normalization & upscaling
└── render/
    ├── StereoRenderer.kt        CPU depth-warp + anaglyph composite
    ├── AnaglyphShader.kt        OpenGL ES shader source
    └── EdgeFadeMask.kt          Dedicated R-channel fade mask
```

## Requirements

- Android 12 (API 31) or higher
- LiteRT 2.1.0 (`com.google.ai.edge.litert:litert:2.1.0`)
- Kotlin 2.2.0, Jetpack Compose, Material 3
- No storage permissions required (Photo Picker + MediaStore insert)

## Model Integration

Place a licensed monocular depth estimation `.tflite` model at:
```
app/src/main/assets/models/depth_model.tflite
```

**Input:** `[1, 384, 384, 3]` float32 RGB, normalized [0, 1]
**Output:** `[1, 384, 384, 1]` float32 depth map

The app initializes with GPU acceleration first, falling back to CPU.

## Building

```bash
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`
