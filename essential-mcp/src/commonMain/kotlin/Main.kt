package io.github.qingshu.essentialmcp

import io.github.qingshu.essentialmcp.disableKotlinLogging
import io.github.qingshu.mcptool.generated.registerGeneratedMcpDeclarations
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    disableKotlinLogging()
    runMcpServerUsingStdio()
}

fun runMcpServerUsingStdio() {
    val server = McpServer(
        name = "essential-mcp",
        version = "1.0.0",
    ) {
        registerGeneratedMcpDeclarations()
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
