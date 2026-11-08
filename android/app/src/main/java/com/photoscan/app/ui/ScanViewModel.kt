package com.photoscan.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoscan.app.pipeline.FrameExtractor
import com.photoscan.app.pipeline.NativeColmap
import com.photoscan.app.pipeline.NativeColmapEngine
import com.photoscan.app.pipeline.ReconstructionEngine
import com.photoscan.app.pipeline.ReconstructionUnavailableException
import com.photoscan.app.pipeline.UnavailableEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Runs one scan at a time: video -> frames -> reconstruction -> model file. */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        data object Idle : State
        data class Running(val message: String) : State
        data class Failed(val message: String) : State
        data class Done(val modelPath: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // COLMAP when libphotoscan_native.so is packaged; otherwise refuse to run.
    private val engine: ReconstructionEngine =
        if (NativeColmap.isAvailable) NativeColmapEngine() else UnavailableEngine()

    fun process(video: Uri) {
        if (_state.value is State.Running) return
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val workDir = File(app.filesDir, "scans/${System.currentTimeMillis()}")
                _state.value = State.Running("Reading video")
                val frames = withContext(Dispatchers.IO) {
                    FrameExtractor.extract(
                        context = app,
                        video = video,
                        outDir = File(workDir, "frames"),
                        fps = 3.0,
                        maxFrames = 120,
                    ) { message -> _state.value = State.Running(message) }
                }
                val model = withContext(Dispatchers.IO) {
                    engine.reconstruct(frames, workDir) { message ->
                        _state.value = State.Running(message)
                    }
                }
                _state.value = State.Done(model.path)
            } catch (e: ReconstructionUnavailableException) {
                _state.value = State.Failed(e.message ?: "Reconstruction is unavailable")
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.toString())
            }
        }
    }

    fun reset() {
        if (_state.value !is State.Running) _state.value = State.Idle
    }
}
