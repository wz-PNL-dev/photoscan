package com.photoscan.app.pipeline

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Converts a binary little-endian PLY triangle mesh (as written by COLMAP) to a
 * glTF binary (GLB). Positions are always written. Normals and vertex colours are
 * written when the PLY has them. Polygons are fan-triangulated.
 */
object PlyToGlb {

    private class Property(val name: String, val type: String, val isList: Boolean, val countType: String?)
    private class Element(val name: String, val count: Int, val properties: List<Property>)

    fun convert(ply: File, glb: File) {
        RandomAccessFile(ply, "r").use { raf ->
            val input = raf.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, raf.length())
                .order(ByteOrder.LITTLE_ENDIAN)
            val mesh = read(input)
            glb.parentFile?.mkdirs()
            glb.writeBytes(Glb.build(mesh))
        }
    }

    internal class Mesh(
        val positions: FloatArray,
        val normals: FloatArray?,
        val colors: FloatArray?,
        val indices: IntArray,
    )

    internal fun read(buf: ByteBuffer): Mesh {
        val elements = parseHeader(buf)
        var positions = FloatArray(0)
        var normals: FloatArray? = null
        var colors: FloatArray? = null
        val indices = ArrayList<Int>()
        var vertexCount = 0

        for (element in elements) {
            when (element.name) {
                "vertex" -> {
                    vertexCount = element.count
                    val names = element.properties.map { it.name }
                    val hasNormals = "nx" in names && "ny" in names && "nz" in names
                    val hasColors = "red" in names && "green" in names && "blue" in names
                    positions = FloatArray(vertexCount * 3)
                    normals = if (hasNormals) FloatArray(vertexCount * 3) else null
                    colors = if (hasColors) FloatArray(vertexCount * 3) else null
                    for (v in 0 until vertexCount) {
                        for (p in element.properties) {
                            require(!p.isList) { "List properties on vertices are not supported" }
                            val value = readScalar(buf, p.type)
                            when (p.name) {
                                "x" -> positions[v * 3] = value.toFloat()
                                "y" -> positions[v * 3 + 1] = value.toFloat()
                                "z" -> positions[v * 3 + 2] = value.toFloat()
                                "nx" -> normals?.set(v * 3, value.toFloat())
                                "ny" -> normals?.set(v * 3 + 1, value.toFloat())
                                "nz" -> normals?.set(v * 3 + 2, value.toFloat())
                                "red" -> colors?.set(v * 3, (value / 255.0).toFloat())
                                "green" -> colors?.set(v * 3 + 1, (value / 255.0).toFloat())
                                "blue" -> colors?.set(v * 3 + 2, (value / 255.0).toFloat())
                            }
                        }
                    }
                }
                "face" -> {
                    for (f in 0 until element.count) {
                        for (p in element.properties) {
                            if (p.isList) {
                                val n = readScalar(buf, p.countType!!).toInt()
                                val face = IntArray(n) { readScalar(buf, p.type).toInt() }
                                if (p.name == "vertex_indices" || p.name == "vertex_index") {
                                    for (k in 1 until n - 1) {
                                        indices += face[0]
                                        indices += face[k]
                                        indices += face[k + 1]
                                    }
                                }
                            } else {
                                readScalar(buf, p.type)
                            }
                        }
                    }
                }
                else -> {
                    for (i in 0 until element.count) {
                        for (p in element.properties) {
                            if (p.isList) {
                                val n = readScalar(buf, p.countType!!).toInt()
                                repeat(n) { readScalar(buf, p.type) }
                            } else {
                                readScalar(buf, p.type)
                            }
                        }
                    }
                }
            }
        }
        require(vertexCount > 0) { "PLY has no vertices" }
        require(indices.isNotEmpty()) { "PLY has no faces" }
        for (i in indices) require(i in 0 until vertexCount) { "Face index $i out of range" }
        return Mesh(positions, normals, colors, indices.toIntArray())
    }

    private fun parseHeader(buf: ByteBuffer): List<Element> {
        val lines = ArrayList<String>()
        val sb = StringBuilder()
        while (true) {
            require(buf.hasRemaining()) { "PLY header ends without end_header" }
            val c = buf.get().toInt().toChar()
            if (c == '\n') {
                val line = sb.toString().trim()
                sb.setLength(0)
                lines += line
                if (line == "end_header") break
            } else if (c != '\r') {
                sb.append(c)
            }
        }
        require(lines.first() == "ply") { "Not a PLY file" }
        require(lines.any { it == "format binary_little_endian 1.0" }) {
            "Only binary little-endian PLY is supported"
        }

        val elements = ArrayList<Element>()
        var name: String? = null
        var count = 0
        var props = ArrayList<Property>()
        fun flush() {
            name?.let { elements += Element(it, count, props) }
        }
        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "element" -> {
                    flush()
                    name = parts[1]
                    count = parts[2].toInt()
                    props = ArrayList()
                }
                "property" -> {
                    if (parts[1] == "list") {
                        props += Property(parts[4], parts[3], isList = true, countType = parts[2])
                    } else {
                        props += Property(parts[2], parts[1], isList = false, countType = null)
                    }
                }
            }
        }
        flush()
        return elements
    }

    private fun readScalar(buf: ByteBuffer, type: String): Double = when (type) {
        "char", "int8" -> buf.get().toDouble()
        "uchar", "uint8" -> (buf.get().toInt() and 0xff).toDouble()
        "short", "int16" -> buf.short.toDouble()
        "ushort", "uint16" -> (buf.short.toInt() and 0xffff).toDouble()
        "int", "int32" -> buf.int.toDouble()
        "uint", "uint32" -> (buf.int.toLong() and 0xffffffffL).toDouble()
        "float", "float32" -> buf.float.toDouble()
        "double", "float64" -> buf.double
        else -> throw IllegalArgumentException("Unknown PLY type: $type")
    }
}

/** Minimal GLB writer: one mesh, one primitive, flat grey material. */
internal object Glb {
    fun build(mesh: PlyToGlb.Mesh): ByteArray {
        val vertexCount = mesh.positions.size / 3
        val binary = ByteBuffer.allocate(
            mesh.positions.size * 4 +
                (mesh.normals?.size ?: 0) * 4 +
                (mesh.colors?.size ?: 0) * 4 +
                mesh.indices.size * 4,
        ).order(ByteOrder.LITTLE_ENDIAN)

        val positionOffset = 0
        mesh.positions.forEach { binary.putFloat(it) }
        val normalOffset = binary.position()
        mesh.normals?.forEach { binary.putFloat(it) }
        val colorOffset = binary.position()
        mesh.colors?.forEach { binary.putFloat(it) }
        val indexOffset = binary.position()
        mesh.indices.forEach { binary.putInt(it) }

        val min = FloatArray(3) { Float.POSITIVE_INFINITY }
        val max = FloatArray(3) { Float.NEGATIVE_INFINITY }
        for (v in 0 until vertexCount) for (k in 0..2) {
            val value = mesh.positions[v * 3 + k]
            if (value < min[k]) min[k] = value
            if (value > max[k]) max[k] = value
        }

        val accessors = ArrayList<String>()
        val views = ArrayList<String>()
        val attributes = ArrayList<String>()

        fun addView(offset: Int, length: Int, target: Int): Int {
            views += "{\"buffer\":0,\"byteOffset\":$offset,\"byteLength\":$length,\"target\":$target}"
            return views.size - 1
        }
        fun addAccessor(view: Int, count: Int, componentType: Int, type: String, bounds: String = ""): Int {
            accessors += "{\"bufferView\":$view,\"componentType\":$componentType," +
                "\"count\":$count,\"type\":\"$type\"$bounds}"
            return accessors.size - 1
        }

        val posView = addView(positionOffset, mesh.positions.size * 4, 34962)
        val posAcc = addAccessor(
            posView, vertexCount, 5126, "VEC3",
            String.format(
                Locale.US,
                ",\"min\":[%f,%f,%f],\"max\":[%f,%f,%f]",
                min[0], min[1], min[2], max[0], max[1], max[2],
            ),
        )
        attributes += "\"POSITION\":$posAcc"

        if (mesh.normals != null) {
            val view = addView(normalOffset, mesh.normals.size * 4, 34962)
            attributes += "\"NORMAL\":${addAccessor(view, vertexCount, 5126, "VEC3")}"
        }
        if (mesh.colors != null) {
            val view = addView(colorOffset, mesh.colors.size * 4, 34962)
            attributes += "\"COLOR_0\":${addAccessor(view, vertexCount, 5126, "VEC3")}"
        }
        val idxView = addView(indexOffset, mesh.indices.size * 4, 34963)
        val idxAcc = addAccessor(idxView, mesh.indices.size, 5125, "SCALAR")

        val json = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"photoscan\"}," +
            "\"scene\":0,\"scenes\":[{\"nodes\":[0]}]," +
            "\"nodes\":[{\"mesh\":0}]," +
            "\"meshes\":[{\"primitives\":[{\"attributes\":{${attributes.joinToString(",")}}," +
            "\"indices\":$idxAcc,\"material\":0,\"mode\":4}]}]," +
            "\"materials\":[{\"doubleSided\":true,\"pbrMetallicRoughness\":" +
            "{\"baseColorFactor\":[0.8,0.8,0.8,1.0],\"metallicFactor\":0.0,\"roughnessFactor\":0.6}}]," +
            "\"accessors\":[${accessors.joinToString(",")}]," +
            "\"bufferViews\":[${views.joinToString(",")}]," +
            "\"buffers\":[{\"byteLength\":${binary.capacity()}}]}"

        var jsonBytes = json.toByteArray(Charsets.UTF_8)
        jsonBytes += ByteArray((4 - jsonBytes.size % 4) % 4) { ' '.code.toByte() }
        val binBytes = binary.array()
        val binPadded = binBytes + ByteArray((4 - binBytes.size % 4) % 4)

        val total = 12 + 8 + jsonBytes.size + 8 + binPadded.size
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.put("glTF".toByteArray(Charsets.US_ASCII)).putInt(2).putInt(total)
        out.putInt(jsonBytes.size).put("JSON".toByteArray(Charsets.US_ASCII)).put(jsonBytes)
        out.putInt(binPadded.size).put("BIN\u0000".toByteArray(Charsets.US_ASCII)).put(binPadded)
        return out.array()
    }
}
