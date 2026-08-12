package com.craigcollins.anaglyph.domain

import android.graphics.Bitmap
import android.net.Uri

/**
 * Immutable snapshot of the editor's rendering state.
 * Used by [EditorViewModel] and observed by the Compose UI.
 */
data class RenderState(
    val sourceUri: Uri? = null,
    val originalBitmap: Bitmap? = null,
    val depthBitmap: Bitmap? = null,
    val anaglyphBitmap: Bitmap? = null,
    val settings: EditorSettings = EditorSettings(),
    val isProcessing: Boolean = false,
    val statusMessage: String = "Select an image to begin",
    val modelAvailable: Boolean = false,
    val exportedUri: Uri? = null,
)
