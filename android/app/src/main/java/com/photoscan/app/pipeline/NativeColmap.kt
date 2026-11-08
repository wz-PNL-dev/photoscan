package com.photoscan.app.pipeline

/**
 * JNI entry point for the COLMAP library built from android/native.
 * Loading fails cleanly when the library is not in the APK.
 */
object NativeColmap {
    /** True when libphotoscan_native.so is packaged and loads. */
    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("photoscan_native")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    /**
     * Runs sparse and dense reconstruction over the images in [imageDir], writing to
     * [workspaceDir]. Returns the number of sparse models found (0 means failure).
     * Blocking and CPU-heavy: call from a background thread.
     */
    @JvmStatic
    external fun nativeReconstruct(
        imageDir: String,
        workspaceDir: String,
        lowQuality: Boolean,
    ): Int
}
