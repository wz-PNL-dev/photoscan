package com.photoscan.app.pipeline

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlyToGlbTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** A unit square as two triangles, with normals and colours, binary little-endian. */
    private fun squarePly(): ByteArray {
        val header = listOf(
            "ply",
            "format binary_little_endian 1.0",
            "element vertex 4",
            "property float x",
            "property float y",
            "property float z",
            "property float nx",
            "property float ny",
            "property float nz",
            "property uchar red",
            "property uchar green",
            "property uchar blue",
            "element face 2",
            "property list uchar int vertex_indices",
            "end_header",
        ).joinToString("\n", postfix = "\n").toByteArray(Charsets.US_ASCII)

        val vertices = listOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(1f, 1f, 0f),
            floatArrayOf(0f, 1f, 0f),
        )
        val faces = listOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 3))
        // Vertex: 3 floats position, 3 floats normal, 3 bytes colour = 27 bytes.
        // Face: 1 byte count, then 3 ints = 13 bytes.
        val body = ByteBuffer.allocate(vertices.size * 27 + faces.size * 13)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (v in vertices) {
            body.putFloat(v[0]).putFloat(v[1]).putFloat(v[2])
            body.putFloat(0f).putFloat(0f).putFloat(1f)
            body.put(255.toByte()).put(0).put(0)
        }
        for (tri in faces) {
            body.put(3)
            for (i in tri) body.putInt(i)
        }
        return header + body.array()
    }

    @Test
    fun convertsTrianglesToGlbWithPositionsNormalsAndColours() {
        val ply = tmp.newFile("mesh.ply")
        ply.writeBytes(squarePly())
        val glb = File(tmp.root, "out/model.glb")

        PlyToGlb.convert(ply, glb)

        val bytes = glb.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("glTF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(2, buf.getInt(4))
        assertEquals(bytes.size, buf.getInt(8))
        val jsonLength = buf.getInt(12)
        val json = String(bytes, 20, jsonLength, Charsets.UTF_8)
        assertTrue(json.contains("\"POSITION\":0"))
        assertTrue(json.contains("\"NORMAL\":1"))
        assertTrue(json.contains("\"COLOR_0\":2"))
        // Two triangles: six indices.
        assertTrue(json.contains("\"count\":6,\"type\":\"SCALAR\""))
        // Bounding box of the unit square.
        assertTrue(json.contains("\"min\":[0.000000,0.000000,0.000000]"))
        assertTrue(json.contains("\"max\":[1.000000,1.000000,0.000000]"))
    }

    @Test
    fun rejectsAsciiPly() {
        val ply = tmp.newFile("ascii.ply")
        ply.writeText("ply\nformat ascii 1.0\nelement vertex 0\nend_header\n")
        try {
            PlyToGlb.convert(ply, File(tmp.root, "x.glb"))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("binary little-endian"))
        }
    }
}
