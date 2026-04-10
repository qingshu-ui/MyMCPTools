@file:Suppress("FunctionName", "SpellCheckingInspection")

package io.github.qingshu.process

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import platform.posix.SIGKILL
import platform.posix.SIGTERM
import platform.posix.WNOHANG
import platform.posix.kill
import platform.posix.waitpid

fun WIFEXITED(status: Int) = (status and 0x7F) == 0
fun WEXITSTATUS(status: Int) = (status shr 8) and 0xFF
fun WIFSIGNALED(status: Int) = (status and 0xFF) != 0 && (status and 0x7F) != 0x7F
fun WTERMSIG(status: Int) = status and 0x7F

actual class Process internal constructor(
    actual val pid: Long,
    stdinFd: Int,
    stdoutFd: Int,
    stderrFd: Int,
) {
    actual val stdin: RawSink = FdSink(stdinFd)
    actual val stdout: RawSource = FdSource(stdoutFd)
    actual val stderr: RawSource = FdSource(stderrFd)

    @OptIn(ExperimentalForeignApi::class)
    actual fun waitFor(): Int = memScoped {
        val st = alloc<IntVar>()
        waitpid(pid.toInt(), st.ptr, 0)
        WEXITSTATUS(st.value)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun exitCode(): Int? = memScoped {
        val st = alloc<IntVar>()
        val r = waitpid(pid.toInt(), st.ptr, WNOHANG)
        if (r <= 0) null else WEXITSTATUS(st.value)
    }

    actual fun destroy() {
        kill(pid.toInt(), SIGTERM)
    }

    actual fun destroyForcibly() {
        kill(pid.toInt(), SIGKILL)
    }

    actual companion object
}
