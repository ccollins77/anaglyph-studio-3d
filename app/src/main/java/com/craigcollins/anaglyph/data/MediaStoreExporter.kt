package com.craigcollins.anaglyph.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

/**
 * Exports anaglyph bitmaps to the shared Pictures/Anaglyph directory
 * via [MediaStore], with no broad storage permissions required.
 *
 * Uses [MediaStore.Images.Media.IS_PENDING] to keep the file private
 * to the app while writing, then publishes it.
 */
object MediaStoreExporter {

    /**
     * Export a [Bitmap] to Pictures/Anaglyph as PNG or JPEG.
     *
     * @param context  Application/content context
     * @param bitmap   The anaglyph bitmap to export
     * @param format   Bitmap.CompressFormat.PNG or JPEG
     * @param quality  Compression quality (0–100), ignored for PNG
     * @param name     File display name (without extension)
     * @return the [Uri] of the exported image, or null on failure
     */
    fun export(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100,
        name: String = "anaglyph_${System.currentTimeMillis()}",
    ): Uri? {
        val resolver = context.contentResolver
        val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
        val mimeType = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
        val displayName = "$name.$extension"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Anaglyph")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return null

        try {
            val outputStream: OutputStream? = resolver.openOutputStream(uri)
            if (outputStream != null) {
                outputStream.use {
                    bitmap.compress(format, quality, it)
                }
            } else {
                resolver.delete(uri, null, null)
                return null
            }

            // Publish the file
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }
}
