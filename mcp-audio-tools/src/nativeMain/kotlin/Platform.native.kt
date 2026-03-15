package io.github.qingshu.mcpaudiotools

import io.github.oshai.kotlinlogging.FormattingAppender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import platform.posix.getenv

expect fun stdin(): RawSource
expect fun stdout(): RawSink

class NativeProcess : Process {
    override val input: Source = stdin().buffered()
    override val output: Sink = stdout().buffered()
}

actual fun platformProcess(): Process = NativeProcess()

actual fun disableKotlinLogging() {
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

@OptIn(ExperimentalForeignApi::class)
actual fun getEnv(key: String): String? = getenv(key)?.toKString()
