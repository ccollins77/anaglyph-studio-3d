package com.craigcollins.anaglyph.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/**
 * EXIF-aware image loader that decodes a [Uri] into a [Bitmap],
 * applying any rotation/flip metadata so the bitmap is upright.
 *
 * Downsamples large images to avoid OOM while preserving enough
 * resolution for the final anaglyph render.
 */
object BitmapLoader {

    private const val MAX_DIMENSION = 2048

    /**
     * Load and orient a bitmap from a content [Uri].
     *
     * @param context   Application/content context
     * @param uri       Content URI of the image (from Photo Picker, gallery, etc.)
     * @param maxDim    Maximum dimension in pixels (default 2048 for quality)
     * @return an upright [Bitmap], or null if decoding fails
     */
    fun load(context: Context, uri: Uri, maxDim: Int = MAX_DIMENSION): Bitmap? {
        val resolver = context.contentResolver

        // First pass: decode bounds only to determine sample size
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        val sampleSize = calculateSampleSize(srcW, srcH, maxDim)

        // Second pass: decode at the computed sample size
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        // Apply EXIF orientation
        val oriented = applyExifOrientation(context, uri, rawBitmap)
        return oriented
    }

    /**
     * Calculate the largest sample size that keeps both dimensions
     * at or below [maxDim].
     */
    private fun calculateSampleSize(srcW: Int, srcH: Int, maxDim: Int): Int {
        var sample = 1
        while (srcW / sample > maxDim || srcH / sample > maxDim) {
            sample *= 2
        }
        return sample
    }

    /**
     * Read EXIF orientation from the image stream and apply a rotation/flip
     * matrix to the bitmap so it displays upright.
     */
    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val exif: ExifInterface? = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it)
            }
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap // ORIENTATION_NORMAL or unknown
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}
