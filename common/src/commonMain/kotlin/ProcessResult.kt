package io.github.qingshu.mcptool.common

/**
 * Represents the result of executing a process.
 * @param code the exit code of the process
 * @param stdout the standard output of the process
 * @param stderr the standard error of the process
 */
data class ProcessResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
)
