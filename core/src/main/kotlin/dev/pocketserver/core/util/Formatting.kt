package dev.pocketserver.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Formatting {
    private val timestampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    fun timestamp(epochMs: Long): String = timestampFormat.format(Instant.ofEpochMilli(epochMs))

    fun bytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var amount = value.toDouble()
        var unit = -1
        while (amount >= 1024 && unit < units.lastIndex) {
            amount /= 1024
            unit++
        }
        return "%.1f %s".format(amount, units[unit])
    }

    fun duration(ms: Long): String {
        if (ms < 1_000) return "${ms}ms"
        val seconds = ms / 1_000
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        if (minutes < 60) return "${minutes}m ${seconds % 60}s"
        return "${minutes / 60}h ${minutes % 60}m"
    }
}
