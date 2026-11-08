package com.photoscan.app.domain

/**
 * Chooses which video timestamps to keep, at roughly [fps] frames per second.
 * Mirrors the intent of `photoscan.frames.extract_frames`, but works in time
 * rather than native frame indices because Android decodes by timestamp.
 */
object FrameSampler {
    /** Timestamps in microseconds, starting at 0, strictly before [durationUs]. */
    fun timestampsUs(durationUs: Long, fps: Double, maxFrames: Int? = null): List<Long> {
        require(fps > 0) { "fps must be positive" }
        require(durationUs >= 0) { "duration must not be negative" }
        if (maxFrames != null) require(maxFrames > 0) { "maxFrames must be positive" }

        val intervalUs = 1_000_000.0 / fps
        val out = ArrayList<Long>()
        var i = 0
        while (true) {
            val t = Math.round(i * intervalUs)
            if (t >= durationUs) break
            out += t
            if (maxFrames != null && out.size >= maxFrames) break
            i++
        }
        return out
    }
}
