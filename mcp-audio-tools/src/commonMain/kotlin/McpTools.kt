package io.github.qingshu.mcpaudiotools

import io.github.qingshu.mcptool.generated.registerGeneratedMcpDeclarations
import io.modelcontextprotocol.kotlin.sdk.server.Server

fun Server.mcpToolRegistry() {
    registerGeneratedMcpDeclarations()
}
