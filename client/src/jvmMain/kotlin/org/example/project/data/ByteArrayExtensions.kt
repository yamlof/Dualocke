package org.example.project.data

fun ByteArray.readUInt16LE(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)
}

fun ByteArray.readUInt32LE(offset: Int): Long {
    return (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}