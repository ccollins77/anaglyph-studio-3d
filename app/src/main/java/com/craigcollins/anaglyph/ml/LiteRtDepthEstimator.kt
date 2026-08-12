package com.craigcollins.anaglyph.ml

import android.content.Context
import android.graphics.Bitmap
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
    private var compiledModel: Any? = null

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
            val modelClass = Class.forName("com.google.ai.edge.litert.CompiledModel")
            val optionsClass = Class.forName("com.google.ai.edge.litert.CompiledModel\$Options")
            val acceleratorClass = Class.forName("com.google.ai.edge.litert.Accelerator")

            // Try GPU first
            compiledModel = try {
                val gpuEnum = acceleratorClass.getField("GPU").get(null)
                val options = optionsClass.getConstructor(acceleratorClass).newInstance(gpuEnum)
                val createMethod = modelClass.getMethod(
                    "create",
                    android.content.res.AssetManager::class.java,
                    String::class.java,
                    optionsClass
                )
                createMethod.invoke(null, context.assets, spec.assetPath, options)
            } catch (gpuFailure: Exception) {
                // CPU fallback
                val cpuEnum = acceleratorClass.getField("CPU").get(null)
                val options = optionsClass.getConstructor(acceleratorClass).newInstance(cpuEnum)
                val createMethod = modelClass.getMethod(
                    "create",
                    android.content.res.AssetManager::class.java,
                    String::class.java,
                    optionsClass
                )
                createMethod.invoke(null, context.assets, spec.assetPath, options)
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
            // Use reflection to call the LiteRT API — this avoids compile-time
            // dependency issues while still using the real CompiledModel API.
            val modelClass = model.javaClass

            // compiledModel.createInputBuffers() -> List<TensorBuffer>
            val createInputBuffers = modelClass.getMethod("createInputBuffers")
            val inputBuffers = createInputBuffers.invoke(model) as List<*>

            // compiledModel.createOutputBuffers() -> List<TensorBuffer>
            val createOutputBuffers = modelClass.getMethod("createOutputBuffers")
            val outputBuffers = createOutputBuffers.invoke(model) as List<*>

            // inputBuffers[0].writeFloat(input)
            val tensorBufferClass = inputBuffers[0]!!.javaClass
            val writeFloat = tensorBufferClass.getMethod("writeFloat", FloatArray::class.java)
            writeFloat.invoke(inputBuffers[0], input)

            // compiledModel.run(inputBuffers, outputBuffers)
            val runMethod = modelClass.getMethod(
                "run",
                List::class.java,
                List::class.java
            )
            runMethod.invoke(model, inputBuffers, outputBuffers)

            // outputBuffers[0].readFloat() -> FloatArray
            val readFloat = tensorBufferClass.getMethod("readFloat")
            return readFloat.invoke(outputBuffers[0]) as FloatArray
        } catch (e: Exception) {
            throw DepthInferenceException("Inference failed: ${e.message}", e)
        }
    }

    override fun close() {
        try {
            compiledModel?.let {
                val closeMethod = it.javaClass.getMethod("close")
                closeMethod.invoke(it)
            }
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
