package org.example.project.data

import java.io.File

object SaveFileManager {

    fun readSaveFile(path: String): ByteArray {
        return File(path).readBytes()
    }

    fun getActiveFireRedSaveBase(data: ByteArray): Int {
        fun maxSaveIndex(base: Int): Long {
            var max = -1L
            repeat(14) { i ->
                val section = base + i * 0x1000
                val id = data.readUInt16LE(section + 0x0FF4)
                if (id in 0..13) {
                    val saveIndex = data.readUInt32LE(section + 0x0FFC)
                    max = maxOf(max, saveIndex)
                }
            }
            return max
        }

        val baseA = 0x00000
        val baseB = 0x0E000

        val a = maxSaveIndex(baseA)
        val b = maxSaveIndex(baseB)

        return if (b >= a) baseB else baseA
    }

    fun findSection(data: ByteArray, base: Int, targetId: Int): Int {
        repeat(14) { i ->
            val section = base + i * 0x1000
            val id = data.readUInt16LE(section + 0xFF4)
            if (id == targetId) return section
        }
        error("Section $targetId not found")
    }
}



