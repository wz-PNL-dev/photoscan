package com.photoscan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProcessingScreen(
    vm: ScanViewModel,
    onModelReady: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state) {
        val done = state as? ScanViewModel.State.Done
        if (done != null) onModelReady(done.modelPath)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is ScanViewModel.State.Running -> {
                    CircularProgressIndicator()
                    Text(s.message, modifier = Modifier.padding(top = 16.dp))
                }

                is ScanViewModel.State.Failed -> {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) { Text("Back") }
                }

                else -> Text("Preparing…")
            }
        }
    }
}
