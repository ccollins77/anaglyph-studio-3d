package com.craigcollins.anaglyph.ui

import androidx.compose.runtime.Composable
import com.craigcollins.anaglyph.ui.theme.AnaglyphTheme

/**
 * Root composable for the Anaglyph app.
 * Wraps the editor screen in the app theme.
 */
@Composable
fun AnaglyphApp() {
    AnaglyphTheme {
        EditorScreen()
    }
}
