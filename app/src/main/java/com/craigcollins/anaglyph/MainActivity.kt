package com.craigcollins.anaglyph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.craigcollins.anaglyph.ui.AnaglyphApp

/**
 * Single-activity host for the Compose-based anaglyph editor.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnaglyphApp()
        }
    }
}
