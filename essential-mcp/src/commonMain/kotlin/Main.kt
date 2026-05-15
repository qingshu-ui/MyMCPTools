package io.github.qingshu.essentialmcp

import io.github.qingshu.essentialmcp.disableKotlinLogging
import io.github.qingshu.essentialmcp.getEnv
import io.github.qingshu.mcptool.generated.registerExecuteCommandTool
import io.github.qingshu.mcptool.generated.registerSubtitleToLrcTool
import io.github.qingshu.mcptool.generated.registerTranscodeWavToMp3Tool
import io.github.qingshu.mcptool.generated.registerUnderstandImageTool
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
        registerExecuteCommandTool()
        registerSubtitleToLrcTool()
        registerTranscodeWavToMp3Tool()
        if (getEnv(VISION_API_KEY) != null) {
            registerUnderstandImageTool()
        }
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
