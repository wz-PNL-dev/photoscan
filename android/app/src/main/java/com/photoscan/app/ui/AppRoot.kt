package com.photoscan.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

sealed interface ModelSource {
    /** A model bundled in the app's assets folder, e.g. "sample_car.glb". */
    data class AssetFile(val name: String) : ModelSource

    /** A model on local storage, as an absolute path. */
    data class LocalFile(val path: String) : ModelSource
}

private sealed interface Screen {
    data object Home : Screen
    data object Processing : Screen
    data class Viewer(val source: ModelSource) : Screen
}

@Composable
fun AppRoot(vm: ScanViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    BackHandler(enabled = screen != Screen.Home) {
        vm.reset()
        screen = Screen.Home
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            onVideoChosen = { uri ->
                vm.process(uri)
                screen = Screen.Processing
            },
            onOpenSample = { screen = Screen.Viewer(ModelSource.AssetFile("sample_car.glb")) },
        )

        Screen.Processing -> ProcessingScreen(
            vm = vm,
            onModelReady = { path -> screen = Screen.Viewer(ModelSource.LocalFile(path)) },
            onBack = {
                vm.reset()
                screen = Screen.Home
            },
        )

        is Screen.Viewer -> ViewerScreen(
            source = current.source,
            onBack = { screen = Screen.Home },
        )
    }
}
