package io.github.qingshu.mcptool.common

data class ProcessResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
)
