package com.photoscan.app.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.photoscan.app.domain.FrameSampler
import java.io.File
import java.io.FileOutputStream

/** Extracts JPEG frames from a video using Android's own decoder. */
object FrameExtractor {
    fun extract(
        context: Context,
        video: Uri,
        outDir: File,
        fps: Double,
        maxFrames: Int,
        jpegQuality: Int = 95,
        onProgress: (String) -> Unit,
    ): List<File> {
        outDir.mkdirs()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, video)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: throw IllegalStateException("Could not read the video duration")
            val stamps = FrameSampler.timestampsUs(durationMs * 1000L, fps, maxFrames)
            if (stamps.isEmpty()) throw IllegalStateException("The video is too short to sample")

            val saved = ArrayList<File>(stamps.size)
            stamps.forEachIndexed { index, timeUs ->
                val bitmap: Bitmap = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                ) ?: return@forEachIndexed
                val file = File(outDir, "frame_%05d.jpg".format(saved.size))
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                }
                bitmap.recycle()
                saved += file
                onProgress("Extracting frames: ${index + 1} / ${stamps.size}")
            }
            if (saved.isEmpty()) throw IllegalStateException("No frames could be decoded")
            return saved
        } finally {
            retriever.release()
        }
    }
}
