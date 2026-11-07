package dev.pocketserver.runtime

import java.io.File

data class ElfInfo(
    val is64Bit: Boolean,
    val isLittleEndian: Boolean,
    val type: Int,
    val machine: Int,
) {
    val isAarch64: Boolean get() = is64Bit && isLittleEndian && machine == ElfInspector.EM_AARCH64
    val isExecutable: Boolean get() = type == ElfInspector.ET_EXEC || type == ElfInspector.ET_DYN
    val isUsable: Boolean get() = isAarch64 && isExecutable

    fun describe(): String =
        "64-bit=${is64Bit}, littleEndian=$isLittleEndian, type=$type, machine=$machine"
}

object ElfInspector {
    const val EM_AARCH64 = 183
    const val ET_EXEC = 2
    const val ET_DYN = 3

    fun inspect(file: File): ElfInfo? {
        if (!file.isFile || file.length() < 20) return null
        val header = ByteArray(20)
        file.inputStream().use { stream ->
            if (stream.read(header) != header.size) return null
        }
        if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
        ) {
            return null
        }
        val is64 = header[4].toInt() == 2
        val littleEndian = header[5].toInt() == 1
        val type = readU16(header, 16, littleEndian)
        val machine = readU16(header, 18, littleEndian)
        return ElfInfo(is64Bit = is64, isLittleEndian = littleEndian, type = type, machine = machine)
    }

    fun isUsableAarch64Executable(file: File): Boolean = inspect(file)?.isUsable == true

    private fun readU16(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        val low = bytes[offset].toInt() and 0xFF
        val high = bytes[offset + 1].toInt() and 0xFF
        return if (littleEndian) low or (high shl 8) else (low shl 8) or high
    }
}
