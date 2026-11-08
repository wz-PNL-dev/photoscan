package com.photoscan.app.pipeline

import java.io.File

/** Raised when no reconstruction backend is compiled into this build. */
class ReconstructionUnavailableException(message: String) : Exception(message)

/**
 * Turns a set of frames into a textured or untextured model file.
 * The returned file is a glTF binary (.glb) that the viewer can open.
 */
interface ReconstructionEngine {
    val name: String

    /** Runs on a background thread. Calls [onProgress] with short status lines. */
    suspend fun reconstruct(
        frames: List<File>,
        workDir: File,
        onProgress: (String) -> Unit,
    ): File
}

/**
 * Placeholder used until the native COLMAP backend is built for Android.
 * It refuses to run instead of returning a fake model.
 */
class UnavailableEngine : ReconstructionEngine {
    override val name: String = "none"

    override suspend fun reconstruct(
        frames: List<File>,
        workDir: File,
        onProgress: (String) -> Unit,
    ): File {
        throw ReconstructionUnavailableException(
            "No reconstruction engine is built into this app yet. " +
                "${frames.size} frames were extracted and saved in ${workDir.path}.",
        )
    }
}
