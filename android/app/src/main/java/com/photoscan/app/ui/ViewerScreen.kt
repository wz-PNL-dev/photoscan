package com.photoscan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photoscan.app.domain.Point3
import com.photoscan.app.domain.Scale
import com.photoscan.app.domain.ScaleReference
import com.photoscan.app.domain.ScaleResult
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView

/** What the viewer does with two picked points. */
private enum class PickMode { Calibrate, Measure }

@Composable
fun ViewerScreen(
    source: ModelSource,
    onBack: () -> Unit,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val view = rememberView(engine)
    val collision = rememberCollisionSystem(view)

    val modelInstance = when (source) {
        is ModelSource.AssetFile -> rememberModelInstance(modelLoader, source.name)
        is ModelSource.LocalFile -> rememberModelInstance(
            modelLoader = modelLoader,
            fileLocation = "file://${source.path}",
        )
    }

    // Orbit around the model's centre. Reset changes the key, which rebuilds the
    // camera at its starting framing.
    var resetKey by remember { mutableIntStateOf(0) }
    val cameraManipulator = rememberCameraManipulator(orbitRadius = 4f, key = resetKey)

    var mode by remember { mutableStateOf(PickMode.Calibrate) }
    val pending = remember { mutableStateListOf<Point3>() }
    val references = remember { mutableStateListOf<ScaleReference>() }
    var lastMeasure by remember { mutableStateOf<Double?>(null) }
    var askForMm by remember { mutableStateOf(false) }

    val result: ScaleResult? = remember(references.size, references.toList()) {
        if (references.isEmpty()) null else Scale.compute(references.toList())
    }

    val gestures = rememberOnGestureListener(
        onSingleTapConfirmed = { event, _ ->
            val hit = collision.hitTest(event).firstOrNull() ?: return@rememberOnGestureListener
            val p = hit.getWorldPosition()
            val point = Point3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())
            if (pending.size >= 2) pending.clear()
            pending += point
            if (pending.size == 2) {
                when (mode) {
                    PickMode.Calibrate -> askForMm = true
                    PickMode.Measure -> {
                        lastMeasure = pending[0].distanceTo(pending[1]) * (result?.factor ?: 1.0)
                        pending.clear()
                    }
                }
            }
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            view = view,
            collisionSystem = collision,
            cameraManipulator = cameraManipulator,
            onGestureListener = gestures,
        ) {
            modelInstance?.let {
                ModelNode(
                    modelInstance = it,
                    centerOrigin = Position(0f),
                    autoAnimate = false,
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    OutlinedButton(onClick = { resetKey++ }) { Text("Reset view") }
                }
                Text(
                    "One finger orbits (tilt included), two fingers pan and pinch to zoom.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == PickMode.Calibrate,
                        onClick = { mode = PickMode.Calibrate; pending.clear() },
                        label = { Text("Calibrate") },
                    )
                    FilterChip(
                        selected = mode == PickMode.Measure,
                        onClick = { mode = PickMode.Measure; pending.clear() },
                        label = { Text("Measure") },
                    )
                }
                val hint = when (mode) {
                    PickMode.Calibrate ->
                        "Tap two points, then enter their real distance from the tape measure."
                    PickMode.Measure ->
                        if (result == null) "Calibrate first, then tap two points to measure."
                        else "Tap two points to see their real size in mm."
                }
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                if (pending.size == 1) {
                    Text("First point set. Tap the second point.")
                }
                lastMeasure?.let { Text("Measured: ${"%.1f".format(it)} mm") }
                result?.let { r ->
                    Text(
                        "Scale factor ${"%.4f".format(r.factor)} from ${references.size} reference(s). " +
                            "Spread ${"%.2f".format(r.spreadPct)}%.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!r.consistent) {
                        Text(
                            "References disagree by more than 1%. Re-measure before using this scale.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = {
                        references.clear()
                        lastMeasure = null
                    }) { Text("Clear references") }
                }
            }
        }
    }

    if (askForMm && pending.size == 2) {
        MmDialog(
            onConfirm = { mm ->
                references += ScaleReference(pending[0], pending[1], mm)
                pending.clear()
                askForMm = false
            },
            onDismiss = {
                pending.clear()
                askForMm = false
            },
        )
    }
}

@Composable
private fun MmDialog(
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val mm = text.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Real distance") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Millimetres") },
                isError = text.isNotEmpty() && (mm == null || mm <= 0),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = { mm?.let(onConfirm) },
                enabled = mm != null && mm > 0,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
