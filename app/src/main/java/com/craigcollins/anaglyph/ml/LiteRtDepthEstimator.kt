package com.craigcollins.anaglyph.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.craigcollins.anaglyph.domain.DepthConvention
import com.craigcollins.anaglyph.domain.DepthModelSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device monocular depth estimator backed by LiteRT 2.1.0.
 *
 * Initializes the model with GPU acceleration first, falling back to CPU
 * if the GPU path fails. The model is loaded from [DepthModelSpec.assetPath]
 * in the app's assets directory.
 *
 * If no model file is present, [start] throws [DepthModelMissingException]
 * and the ViewModel falls back to a synthetic depth map.
 *
 * LiteRT API reference: https://developers.google.com/edge/litert/android
 */
class LiteRtDepthEstimator(
    private val context: Context,
    private val spec: DepthModelSpec = DepthModelSpec(),
) : AutoCloseable {

    @Volatile
    private var compiledModel: CompiledModel? = null

    @Volatile
    private var isModelLoaded = false

    /**
     * Check whether the model asset file exists in the app's assets.
     */
    fun isModelAvailable(): Boolean {
        return try {
            context.assets.open(spec.assetPath).use { it.readBytes().isNotEmpty() }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load and compile the depth model.
     *
     * Tries GPU acceleration first, then falls back to CPU.
     *
     * @throws DepthModelMissingException if the model file is not found in assets
     */
    fun start() {
        if (!isModelAvailable()) {
            throw DepthModelMissingException(spec.assetPath)
        }

        try {
            // GPU-first initialization using LiteRT CompiledModel API
            compiledModel = try {
                CompiledModel.create(
                    context.assets,
                    spec.assetPath,
                    CompiledModel.Options(Accelerator.GPU)
                )
            } catch (gpuFailure: Exception) {
                // CPU fallback
                CompiledModel.create(
                    context.assets,
                    spec.assetPath,
                    CompiledModel.Options(Accelerator.CPU)
                )
            }
            isModelLoaded = true
        } catch (e: DepthModelMissingException) {
            throw e
        } catch (e: Exception) {
            throw DepthModelLoadException("Failed to load depth model: ${e.message}", e)
        }
    }

    /**
     * Run depth inference on a preprocessed float array.
     *
     * @param input FloatArray in NHWC layout [1, inputSize, inputSize, 3], normalized [0, 1]
     * @return FloatArray depth map in [1, outputSize, outputSize, 1] layout
     */
    fun infer(input: FloatArray): FloatArray {
        val model = compiledModel ?: throw IllegalStateException("Call start() before infer()")

        try {
            // LiteRT 2.1.0 CompiledModel API
            val inputBuffers = model.createInputBuffers()
            val outputBuffers = model.createOutputBuffers()

            // Write preprocessed input to the first input tensor
            inputBuffers[0].writeFloat(input)

            // Run inference
            model.run(inputBuffers, outputBuffers)

            // Read the depth map from the first output tensor
            return outputBuffers[0].readFloat()
        } catch (e: Exception) {
            throw DepthInferenceException("Inference failed: ${e.message}", e)
        }
    }

    override fun close() {
        try {
            compiledModel?.close()
        } catch (e: Exception) {
            // Best effort
        }
        compiledModel = null
        isModelLoaded = false
    }
}

/**
 * Thrown when the depth model file is not found in assets.
 */
class DepthModelMissingException(val assetPath: String) :
    Exception("Depth model not found at assets/$assetPath")

/**
 * Thrown when the model fails to load or compile.
 */
class DepthModelLoadException(message: String, cause: Throwable) :
    Exception(message, cause)

/**
 * Thrown when inference fails.
 */
class DepthInferenceException(message: String, cause: Throwable) :
    Exception(message, cause)
