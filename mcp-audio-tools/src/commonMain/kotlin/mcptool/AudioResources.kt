package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcptool.annotations.McpResource

@McpResource(
    uri = "audio://server/info",
    name = "audio_server_info",
    description = "Information about the MCP audio tools server.",
    mimeType = "text/plain",
)
fun audioServerInfo(): String = "MCP audio tools server"

@McpResource(
    uriTemplate = "audio://files/{path}/summary",
    name = "audio_file_summary",
    description = "Summary placeholder for an audio file path.",
    mimeType = "text/plain",
)
fun audioFileSummary(path: String): String = "Audio file: $path"
