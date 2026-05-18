package io.github.qingshu.essentialmcp.mcptool

import io.github.qingshu.mcptool.annotations.McpTool
import kotlinx.serialization.Serializable

@Suppress("ktlint:standard:filename")
@Serializable
data class ToolResult(
    val status: String,
    val stdout: String,
    val stderr: String,
)

@McpTool(
    name = "test",
    description = "A test tool",
)
fun test(): ToolResult = ToolResult(
    status = "ok",
    stdout = "",
    stderr = "",
)
