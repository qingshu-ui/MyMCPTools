package io.github.qingshu.mcpaudiotools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun main(args: Array<String>): Unit = runBlocking {
    val mcpServer = Server(
        serverInfo = Implementation(
            name = "MCP Audio Tools",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )

    mcpServer.addTool(
        name = "MCP Audio Tools",
        description = "A tool for audio processing",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("input") {
                    put("type", "string")
                }
            },
        ),
    ) { request ->
        CallToolResult(content = listOf(TextContent("Hello, world!")))
    }

    val process = platformProcess()
    mcpServer.createSession(
        StdioServerTransport(
            inputStream = process.input,
            outputStream = process.output,
        ),
    )
    val job = Job()
    mcpServer.onClose {
        job.complete()
    }
    job.join()
    println()
}

fun runMcpServerUsingStdio() {
    val server = McpServer(
        name = "",
        version = "",
    ) {
    }
    val process = platformProcess()
    runBlocking {
        server.createSession(
            StdioServerTransport(
                inputStream = process.input,
                outputStream = process.output,
            ),
        )
        val hook = Job()
        server.onClose {
            hook.complete()
        }
        hook.join()
    }
}
