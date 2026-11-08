package com.photoscan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Mirrors tests/test_scale.py so both implementations are checked against the same numbers.
class ScaleTest {
    private val origin = Point3(0.0, 0.0, 0.0)

    @Test
    fun singleReferenceGivesRealOverModel() {
        val ref = ScaleReference(origin, Point3(0.0, 0.0, 0.5), realMm = 1000.0)
        val result = Scale.compute(listOf(ref))
        assertEquals(2000.0, result.factor, 1e-9)
        assertTrue(result.consistent)
    }

    @Test
    fun multipleReferencesAreAveragedAndSpreadReported() {
        val refs = listOf(
            ScaleReference(origin, Point3(1.0, 0.0, 0.0), 1000.0),
            ScaleReference(origin, Point3(0.0, 2.0, 0.0), 2000.0),
        )
        val result = Scale.compute(refs)
        assertEquals(1000.0, result.factor, 1e-9)
        assertEquals(0.0, result.spreadPct, 1e-9)
    }

    @Test
    fun inconsistentReferencesAreFlagged() {
        val refs = listOf(
            ScaleReference(origin, Point3(1.0, 0.0, 0.0), 1000.0),
            ScaleReference(origin, Point3(1.0, 0.0, 0.0), 1100.0),
        )
        assertFalse(Scale.compute(refs).consistent)
    }

    @Test
    fun spreadUsesPopulationStandardDeviation() {
        // Factors 1000 and 1200: mean 1100, population stdev 100, so spread 100/1100 * 100.
        val refs = listOf(
            ScaleReference(origin, Point3(1.0, 0.0, 0.0), 1000.0),
            ScaleReference(origin, Point3(1.0, 0.0, 0.0), 1200.0),
        )
        assertEquals(100.0 / 1100.0 * 100.0, Scale.compute(refs).spreadPct, 1e-9)
    }

    @Test
    fun coincidentPointsRejected() {
        try {
            Scale.compute(listOf(ScaleReference(Point3(1.0, 1.0, 1.0), Point3(1.0, 1.0, 1.0), 500.0)))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("coincide"))
        }
    }

    @Test
    fun emptyReferencesRejected() {
        try {
            Scale.compute(emptyList())
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun applyScaleMultipliesEveryCoordinate() {
        val scaled = Scale.apply(listOf(Point3(1.0, 2.0, 3.0)), 10.0)
        assertEquals(listOf(Point3(10.0, 20.0, 30.0)), scaled)
    }
}
