package io.github.qingshu.mcpaudiotools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

@Suppress("FunctionName")
fun McpServer(
    name: String,
    version: String,
    block: Server.() -> Unit,
): Server = Server(
    serverInfo = Implementation(
        name = name,
        version = version,
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
            resources = ServerCapabilities.Resources(subscribe = false, listChanged = true),
            prompts = ServerCapabilities.Prompts(listChanged = true),
            logging = ServerCapabilities.Logging,
        ),
    ),
    block = block,
)
