package com.photoscan.app.pipeline

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reconstruction with COLMAP compiled into the app. Output is the dense Poisson
 * mesh written by COLMAP, converted to a GLB for the viewer.
 */
class NativeColmapEngine(private val lowQuality: Boolean = false) : ReconstructionEngine {
    override val name: String = "colmap-native"

    override suspend fun reconstruct(
        frames: List<File>,
        workDir: File,
        onProgress: (String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (!NativeColmap.isAvailable) {
            throw ReconstructionUnavailableException("The native COLMAP library is not in this build.")
        }
        val imageDir = frames.first().parentFile
            ?: throw IllegalStateException("Frames are not in a folder")
        val workspace = File(workDir, "colmap").apply { mkdirs() }

        onProgress("Reconstructing. This can take a long time on a phone.")
        val models = NativeColmap.nativeReconstruct(imageDir.path, workspace.path, lowQuality)
        if (models == 0) {
            throw IllegalStateException(
                "COLMAP found no model. Shoot a slower orbit with more overlap between views.",
            )
        }

        val mesh = File(workspace, "dense/0/meshed-poisson.ply")
        if (!mesh.exists()) {
            throw IllegalStateException("COLMAP finished without writing a mesh.")
        }
        onProgress("Converting mesh for the viewer")
        val glb = File(workDir, "model.glb")
        PlyToGlb.convert(mesh, glb)
        glb
    }
}
