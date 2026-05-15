package io.github.qingshu.essentialmcp

data class ProcessResult(
    val exitCode: Int,
    val output: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}
