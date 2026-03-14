package io.github.qingshu.mcpaudiotools

import io.github.oshai.kotlinlogging.FormattingAppender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
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
        transcodeWavToMp3()
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

private fun disableKotlinLogging() {
    KotlinLoggingConfiguration.apply {
        appender = object : FormattingAppender() {
            override fun logFormattedMessage(
                loggingEvent: KLoggingEvent,
                formattedMessage: Any?,
            ) {
            }
        }
    }
}
