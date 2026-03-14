package io.github.qingshu.mcptool.common

import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.asSink
import kotlinx.io.asSource
import java.lang.Process

actual class Process internal constructor(private val p: Process) {
    actual val pid: Long
        get() = p.pid()
    actual val stdin: RawSink = p.outputStream.asSink()
    actual val stdout: RawSource = p.inputStream.asSource()
    actual val stderr: RawSource = p.errorStream.asSource()

    actual fun waitFor(): Int = p.waitFor()

    actual fun exitCode(): Int? = runCatching { p.exitValue() }.getOrNull()

    actual fun destroy() {
        p.destroy()
    }

    actual fun destroyForcibly() {
        p.destroyForcibly()
    }

    actual companion object
}
