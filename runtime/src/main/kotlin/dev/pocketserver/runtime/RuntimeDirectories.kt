package dev.pocketserver.runtime

import java.io.File

data class RuntimeDirectories(
    val root: File,
    val packages: File,
    val temp: File,
)
