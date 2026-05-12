package io.github.qingshu.mcpaudiotools

import io.github.qingshu.mcpaudiotools.disableKotlinLogging
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    disableKotlinLogging()
    runMcpServerUsingStdio()
}

fun runMcpServerUsingStdio() {
    val server = McpServer(
        name = "audio-tools",
        version = "1.0.0",
    ) {
        mcpToolRegistry()
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
