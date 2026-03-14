package io.github.qingshu.mcptool.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import platform.windows.DWORDVar
import platform.windows.GetExitCodeProcess
import platform.windows.HANDLE
import platform.windows.INFINITE
import platform.windows.STILL_ACTIVE
import platform.windows.TerminateProcess
import platform.windows.WaitForSingleObject

@OptIn(ExperimentalForeignApi::class)
actual class Process internal constructor(
    private val hProcess: HANDLE,
    private val hThread: HANDLE,
    actual val pid: Long,
    stdinHandle: HANDLE,
    stdoutHandle: HANDLE,
    stderrHandle: HANDLE,
) {
    actual val stdin: RawSink = WinHandleSink(stdinHandle)
    actual val stdout: RawSource = WinHandleSource(stdoutHandle)
    actual val stderr: RawSource = WinHandleSource(stderrHandle)

    actual fun waitFor(): Int = memScoped {
        WaitForSingleObject(hProcess, INFINITE)
        val code = alloc<DWORDVar>()
        GetExitCodeProcess(hProcess, code.ptr)
        code.value.toInt()
    }

    actual fun exitCode(): Int? = memScoped {
        val code = alloc<DWORDVar>()
        GetExitCodeProcess(hProcess, code.ptr)
        if (code.value == STILL_ACTIVE) null else code.value.toInt()
    }

    actual fun destroy() {
        TerminateProcess(hProcess, 1u)
    }

    actual fun destroyForcibly() {
        destroy()
    }

    actual companion object
}
