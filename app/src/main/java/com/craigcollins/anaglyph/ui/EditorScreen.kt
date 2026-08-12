package com.craigcollins.anaglyph.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main editor screen — single-image anaglyph generator.
 *
 * Layout:
 *  - App header with gradient accent
 *  - Image preview area with tabbed views (Original / Depth / Anaglyph)
 *  - Controls card (depth strength, focus, eye swap, edge fade, parallax)
 *  - Action buttons (pick image, export)
 *  - Status bar
 */
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    // Photo Picker — single image selection
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadImage(uri)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Header
        AnaglyphHeader()

        Spacer(modifier = Modifier.height(20.dp))

        // Preview area
        PreviewArea(
            original = state.originalBitmap,
            depth = state.depthBitmap,
            anaglyph = state.anaglyphBitmap,
            isProcessing = state.isProcessing,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status
        StatusRow(
            message = state.statusMessage,
            isProcessing = state.isProcessing,
            modelAvailable = state.modelAvailable,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        ControlsCard(
            settings = state.settings,
            onDepthStrengthChange = viewModel::updateDepthStrength,
            onFocusDepthChange = viewModel::updateFocusDepth,
            onEyeSwapChange = viewModel::updateEyeSwap,
            onEdgeFadeChange = viewModel::updateEdgeFade,
            onMaxParallaxChange = viewModel::updateMaxParallax,
            enabled = state.anaglyphBitmap != null,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    contentDescription = "Pick image",
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Image")
            }

            OutlinedButton(
                onClick = { viewModel.export() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = state.anaglyphBitmap != null,
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Export",
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exported URI notification
        state.exportedUri?.let { uri ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Saved: $uri",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun AnaglyphHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Anaglyph",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Single-image 3D depth anaglyph generator",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreviewArea(
    original: Bitmap?,
    depth: Bitmap?,
    anaglyph: Bitmap?,
    isProcessing: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                anaglyph != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = anaglyph.asImageBitmap(),
                        contentDescription = "Anaglyph preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                depth != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = depth.asImageBitmap(),
                        contentDescription = "Depth map preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                original != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = original.asImageBitmap(),
                        contentDescription = "Original image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select an image to begin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    message: String,
    isProcessing: Boolean,
    modelAvailable: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isProcessing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (modelAvailable)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ControlsCard(
    settings: com.craigcollins.anaglyph.domain.EditorSettings,
    onDepthStrengthChange: (Float) -> Unit,
    onFocusDepthChange: (Float) -> Unit,
    onEyeSwapChange: (Boolean) -> Unit,
    onEdgeFadeChange: (Float) -> Unit,
    onMaxParallaxChange: (Int) -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Controls",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Depth strength
            SliderRow(
                label = "Depth Strength",
                value = settings.depthStrength,
                range = 0f..2f,
                onValueChange = onDepthStrengthChange,
                enabled = enabled,
            )

            // Focus plane
            SliderRow(
                label = "Focus Plane",
                value = settings.focusDepth,
                range = 0f..1f,
                onValueChange = onFocusDepthChange,
                enabled = enabled,
            )

            // Edge fade
            SliderRow(
                label = "Edge Fade",
                value = settings.edgeFade,
                range = 0f..1f,
                onValueChange = onEdgeFadeChange,
                enabled = enabled,
            )

            // Max parallax
            SliderRow(
                label = "Max Parallax",
                value = settings.maxParallaxPx.toFloat(),
                range = 0f..40f,
                onValueChange = { onMaxParallaxChange(it.toInt()) },
                enabled = enabled,
            )

            // Eye swap toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = "Eye swap",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Swap Eyes",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Switch(
                    checked = settings.eyeSwap,
                    onCheckedChange = onEyeSwapChange,
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
        )
    }
}
