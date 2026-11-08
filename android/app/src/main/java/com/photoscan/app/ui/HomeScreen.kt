package com.photoscan.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onVideoChosen: (Uri) -> Unit,
    onOpenSample: () -> Unit,
) {
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onVideoChosen(uri) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("PhotoScan", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Make a true-scale 3D model of a car part from a slow orbit video. " +
                    "Shoot with a tape measure laid against the bodywork, then set the scale " +
                    "from two measured points.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { pickVideo.launch(arrayOf("video/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Choose video") }
            OutlinedButton(
                onClick = onOpenSample,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open sample model") }
        }
    }
}
