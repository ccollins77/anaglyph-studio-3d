package com.craigcollins.anaglyph.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craigcollins.anaglyph.data.BitmapLoader
import com.craigcollins.anaglyph.data.MediaStoreExporter
import com.craigcollins.anaglyph.domain.EditorSettings
import com.craigcollins.anaglyph.domain.RenderState
import com.craigcollins.anaglyph.ml.DepthModelMissingException
import com.craigcollins.anaglyph.ml.DepthPostprocessor
import com.craigcollins.anaglyph.ml.DepthPreprocessor
import com.craigcollins.anaglyph.ml.LiteRtDepthEstimator
import com.craigcollins.anaglyph.render.EdgeFadeMask
import com.craigcollins.anaglyph.render.StereoRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel that orchestrates the single-image anaglyph pipeline:
 *
 * 1. Load image (EXIF-aware decode)
 * 2. Preprocess for depth model (384px, NHWC, normalized)
 * 3. Run LiteRT depth inference (GPU-first, CPU fallback)
 * 4. Postprocess depth map (normalize, invert if needed, upscale)
 * 5. Render anaglyph (depth-based stereo warp + red-cyan composite + edge fade)
 * 6. Export via MediaStore
 *
 * If no depth model is bundled, a synthetic center-weighted depth map
 * is generated so the workflow remains functional.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(RenderState())
    val state: StateFlow<RenderState> = _state.asStateFlow()

    private val depthEstimator = LiteRtDepthEstimator(application)
    private val preprocessor = DepthPreprocessor()
    private val postprocessor = DepthPostprocessor()
    private val renderer = StereoRenderer()

    // Cached depth at full resolution for re-rendering when settings change
    private var fullResolutionDepth: FloatArray? = null

    /**
     * Load an image from a URI and run the full depth estimation pipeline.
     */
    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                sourceUri = uri,
                isProcessing = true,
                statusMessage = "Loading image…",
                exportedUri = null,
            )

            try {
                // Step 1: Decode image with EXIF orientation
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapLoader.load(getApplication(), uri)
                }
                if (bitmap == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        statusMessage = "Failed to decode image",
                    )
                    return@launch
                }

                _state.value = _state.value.copy(
                    originalBitmap = bitmap,
                    statusMessage = "Estimating depth…",
                )

                // Step 2: Preprocess for depth model
                val modelInput = preprocessor.preprocess(bitmap)

                // Step 3: Run depth inference
                val depthMap = withContext(Dispatchers.Default) {
                    runDepthEstimation(modelInput, bitmap)
                }

                // Cache the full-resolution depth for re-rendering
                fullResolutionDepth = depthMap

                // Step 4: Create depth preview bitmap (downsample for UI display)
                val depthPreview = withContext(Dispatchers.Default) {
                    val previewSize = 384
                    // Downsample the full-resolution depth to preview size
                    val previewDepth = resizeDepth(
                        depthMap, bitmap.width, bitmap.height,
                        previewSize, previewSize
                    )
                    postprocessor.toBitmap(previewDepth, previewSize)
                }

                _state.value = _state.value.copy(
                    depthBitmap = depthPreview,
                    statusMessage = "Rendering anaglyph…",
                )

                // Step 5: Render the anaglyph
                renderAnaglyph()

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    statusMessage = "Error: ${e.message ?: "Unknown error"}",
                )
            }
        }
    }

    /**
     * Run depth estimation, with synthetic fallback if no model is bundled.
     */
    private fun runDepthEstimation(modelInput: FloatArray, sourceBitmap: Bitmap): FloatArray {
        return try {
            depthEstimator.start()

            val rawOutput = depthEstimator.infer(modelInput)
            val normalized = postprocessor.normalize(rawOutput)

            // Mark model as available — LiteRT inference succeeded
            _state.value = _state.value.copy(modelAvailable = true)

            // Upscale to source dimensions for rendering
            postprocessor.upscale(
                normalized,
                384, // model output size
                sourceBitmap.width,
                sourceBitmap.height
            )
        } catch (e: DepthModelMissingException) {
            // No model bundled — generate synthetic depth
            _state.value = _state.value.copy(
                modelAvailable = false,
                statusMessage = "No depth model — using synthetic depth",
            )
            generateSyntheticDepth(sourceBitmap.width, sourceBitmap.height)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                statusMessage = "Depth estimation failed — using synthetic depth",
            )
            generateSyntheticDepth(sourceBitmap.width, sourceBitmap.height)
        }
    }

    /**
     * Resize a depth array from src dimensions to dst dimensions using nearest-neighbor.
     */
    private fun resizeDepth(
        depth: FloatArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): FloatArray {
        val output = FloatArray(dstWidth * dstHeight)
        val xRatio = srcWidth.toFloat() / dstWidth
        val yRatio = srcHeight.toFloat() / dstHeight
        for (y in 0 until dstHeight) {
            val srcY = (y * yRatio).toInt().coerceIn(0, srcHeight - 1)
            for (x in 0 until dstWidth) {
                val srcX = (x * xRatio).toInt().coerceIn(0, srcWidth - 1)
                output[y * dstWidth + x] = depth[srcY * srcWidth + srcX]
            }
        }
        return output
    }

    /**
     * Generate a synthetic center-weighted depth map as a fallback
     * when no LiteRT model is bundled.
     *
     * Creates a radial gradient where the center is "near" (1.0) and
     * edges are "far" (0.0), giving a reasonable 3D effect for most images.
     */
    private fun generateSyntheticDepth(width: Int, height: Int): FloatArray {
        val depth = FloatArray(width * height)
        val cx = width / 2f
        val cy = height / 2f
        val maxDist = kotlin.math.hypot(cx, cy)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dist = kotlin.math.hypot((x - cx), (y - cy))
                val normalized = 1f - (dist / maxDist).coerceIn(0f, 1f)
                depth[y * width + x] = normalized
            }
        }
        return depth
    }

    /**
     * Re-render the anaglyph from the cached depth map and current settings.
     */
    fun renderAnaglyph() {
        val bitmap = _state.value.originalBitmap ?: return
        val depth = fullResolutionDepth ?: return
        val settings = _state.value.settings

        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, statusMessage = "Rendering…")

            try {
                val anaglyph = withContext(Dispatchers.Default) {
                    // Generate edge fade mask at source resolution
                    val fadeMask = EdgeFadeMask.createMask(
                        bitmap.width, bitmap.height, settings.edgeFade
                    )
                    renderer.render(bitmap, depth, settings, fadeMask)
                }

                _state.value = _state.value.copy(
                    anaglyphBitmap = anaglyph,
                    isProcessing = false,
                    statusMessage = if (_state.value.modelAvailable)
                        "Anaglyph ready" else "Anaglyph ready (synthetic depth)",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    statusMessage = "Render error: ${e.message}",
                )
            }
        }
    }

    // Settings update methods — each triggers a re-render

    fun updateDepthStrength(value: Float) {
        _state.value = _state.value.copy(settings = _state.value.settings.copy(depthStrength = value))
        renderAnaglyph()
    }

    fun updateFocusDepth(value: Float) {
        _state.value = _state.value.copy(settings = _state.value.settings.copy(focusDepth = value))
        renderAnaglyph()
    }

    fun updateEyeSwap(value: Boolean) {
        _state.value = _state.value.copy(settings = _state.value.settings.copy(eyeSwap = value))
        renderAnaglyph()
    }

    fun updateEdgeFade(value: Float) {
        _state.value = _state.value.copy(settings = _state.value.settings.copy(edgeFade = value))
        renderAnaglyph()
    }

    fun updateMaxParallax(value: Int) {
        _state.value = _state.value.copy(settings = _state.value.settings.copy(maxParallaxPx = value))
        renderAnaglyph()
    }

    /**
     * Export the current anaglyph to Pictures/Anaglyph via MediaStore.
     */
    fun export() {
        val anaglyph = _state.value.anaglyphBitmap ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(statusMessage = "Exporting…")

            try {
                val uri = withContext(Dispatchers.IO) {
                    MediaStoreExporter.export(getApplication(), anaglyph)
                }
                _state.value = _state.value.copy(
                    exportedUri = uri,
                    statusMessage = if (uri != null) "Exported to Pictures/Anaglyph" else "Export failed",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    statusMessage = "Export error: ${e.message}",
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        depthEstimator.close()
    }
}
