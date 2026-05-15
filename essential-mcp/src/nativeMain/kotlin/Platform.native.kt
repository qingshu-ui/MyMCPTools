package io.github.qingshu.essentialmcp

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.curl.Curl
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
}

@OptIn(ExperimentalForeignApi::class)
actual fun getEnv(key: String): String? = getenv(key)?.toKString()

actual val httpClientEngine: HttpClientEngine = Curl.create()
