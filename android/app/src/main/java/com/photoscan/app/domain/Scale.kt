package com.photoscan.app.domain

import kotlin.math.sqrt

/** A point in model space. Units are unitless until a scale is applied. */
data class Point3(val x: Double, val y: Double, val z: Double) {
    fun distanceTo(other: Point3): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * Two model points and the real distance between them, in millimetres.
 * Mirrors `photoscan.scale.ScaleReference`.
 */
data class ScaleReference(val a: Point3, val b: Point3, val realMm: Double) {
    val modelDistance: Double get() = a.distanceTo(b)
}

/**
 * Scale factor (real / model) and how well the references agree.
 * Mirrors `photoscan.scale.ScaleResult`.
 */
data class ScaleResult(
    val factor: Double,
    val spreadPct: Double,
    val perReference: List<Double>,
) {
    /** True when individual references agree within 1 percent. */
    val consistent: Boolean get() = spreadPct <= 1.0
}

object Scale {
    /**
     * Returns the scale factor from one or more references. Several references are
     * averaged and the spread between them is reported, so warping or a bad
     * measurement can be seen before any part is designed from the model.
     */
    fun compute(references: List<ScaleReference>): ScaleResult {
        require(references.isNotEmpty()) { "At least one scale reference is required" }
        val factors = references.map { ref ->
            val model = ref.modelDistance
            require(model > 0) { "Reference points coincide; cannot compute a scale" }
            require(ref.realMm > 0) { "Real distance must be positive" }
            ref.realMm / model
        }
        val avg = factors.average()
        val spread = if (factors.size > 1) {
            // Population standard deviation, as Python's statistics.pstdev.
            val variance = factors.sumOf { (it - avg) * (it - avg) } / factors.size
            sqrt(variance) / avg * 100.0
        } else {
            0.0
        }
        return ScaleResult(factor = avg, spreadPct = spread, perReference = factors)
    }

    /** Scales vertices uniformly about the origin. */
    fun apply(vertices: List<Point3>, factor: Double): List<Point3> =
        vertices.map { Point3(it.x * factor, it.y * factor, it.z * factor) }
}
