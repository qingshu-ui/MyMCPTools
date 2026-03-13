package io.github.qingshu.mcpaudiotools

data class ProcessResult(
    val exitCode: Int,
    val output: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}
