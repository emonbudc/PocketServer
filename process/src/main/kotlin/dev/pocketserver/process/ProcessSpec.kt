package dev.pocketserver.process

import java.io.File

data class ProcessSpec(
    val id: String,
    val executable: File,
    val arguments: List<String> = emptyList(),
    val workingDirectory: File,
    val environment: Map<String, String> = emptyMap(),
    val logFile: File,
) {
    init {
        require(ID_PATTERN.matches(id)) { "Invalid process id: $id" }
        require(executable.isFile) { "Executable does not exist: ${executable.absolutePath}" }
        require(workingDirectory.isDirectory) { "Working directory does not exist: ${workingDirectory.absolutePath}" }
        require(arguments.none { it.contains('\u0000') }) { "Arguments may not contain NUL bytes" }
        environment.forEach { (name, value) ->
            require(NAME_PATTERN.matches(name)) { "Invalid environment variable name: $name" }
            require(!value.contains('\u0000')) { "Environment values may not contain NUL bytes" }
        }
    }

    fun commandLine(): List<String> = listOf(executable.absolutePath) + arguments

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private val NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
