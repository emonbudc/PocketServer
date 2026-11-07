package dev.pocketserver.core.util

import java.io.File

class UnsafePathException(message: String) : IllegalArgumentException(message)

object PathGuard {
    fun isValidRelativePath(raw: String): Boolean {
        if (raw.isBlank() || raw.length > 4096) return false
        if (raw.any { it.isISOControl() }) return false
        if (raw.contains('\\') || raw.contains('\u0000')) return false
        if (raw.startsWith('/') || raw.startsWith('~')) return false
        if (raw.length >= 2 && raw[1] == ':') return false
        val segments = raw.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
        return true
    }

    fun resolveWithin(root: File, relativePath: String): File {
        if (!isValidRelativePath(relativePath)) {
            throw UnsafePathException("Refusing unsafe path: $relativePath")
        }
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relativePath).canonicalFile
        if (candidate != canonicalRoot && !candidate.toPath().startsWith(canonicalRoot.toPath())) {
            throw UnsafePathException("Path escapes root: $relativePath")
        }
        return candidate
    }

    fun isInside(root: File, file: File): Boolean = try {
        file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
    } catch (_: Exception) {
        false
    }
}
