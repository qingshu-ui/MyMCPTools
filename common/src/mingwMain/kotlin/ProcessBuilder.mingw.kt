package io.github.qingshu.mcptool.common

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import platform.windows.CREATE_NO_WINDOW
import platform.windows.CloseHandle
import platform.windows.CreatePipe
import platform.windows.CreateProcessW
import platform.windows.FreeEnvironmentStringsW
import platform.windows.GetEnvironmentStringsW
import platform.windows.GetLastError
import platform.windows.HANDLEVar
import platform.windows.HANDLE_FLAG_INHERIT
import platform.windows.PROCESS_INFORMATION
import platform.windows.SECURITY_ATTRIBUTES
import platform.windows.STARTF_USESTDHANDLES
import platform.windows.STARTUPINFOW
import platform.windows.SetHandleInformation
import platform.windows.TRUE
import platform.windows.WCHARVar

actual class ProcessBuilder actual constructor(vararg command: String) {
    private val cmdLine = command.joinToString(" ") { escapeArg(it) }
    private var workDir: String? = null
    private var mergeErr = false
    private val extraEnv = mutableMapOf<String, String>()

    actual fun directory(dir: String): ProcessBuilder = apply { workDir = dir }

    actual fun mergeStderr(merge: Boolean): ProcessBuilder = apply { mergeErr = merge }

    actual fun environment(key: String, value: String): ProcessBuilder = apply { extraEnv[key] = value }

    @OptIn(ExperimentalForeignApi::class)
    actual fun start(): Process = memScoped {
        val sa = alloc<SECURITY_ATTRIBUTES>().apply {
            nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
            bInheritHandle = TRUE
            lpSecurityDescriptor = null
        }
        val sir = alloc<HANDLEVar>()
        val sor = alloc<HANDLEVar>()
        val ser = alloc<HANDLEVar>()

        val siw = alloc<HANDLEVar>()
        val sow = alloc<HANDLEVar>()
        val sew = alloc<HANDLEVar>()

        CreatePipe(sir.ptr, siw.ptr, sa.ptr, 0u)
        CreatePipe(sor.ptr, sow.ptr, sa.ptr, 0u)
        if (!mergeErr) CreatePipe(ser.ptr, sew.ptr, sa.ptr, 0u)

        SetHandleInformation(siw.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)
        SetHandleInformation(sor.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)
        if (!mergeErr) SetHandleInformation(ser.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)

        val si = alloc<STARTUPINFOW>().apply {
            cb = sizeOf<STARTUPINFOW>().toUInt()
            dwFlags = STARTF_USESTDHANDLES.toUInt()
            hStdInput = sir.value
            hStdOutput = sow.value
            hStdError = if (mergeErr) sow.value else sew.value
        }
        val pi = alloc<PROCESS_INFORMATION>()

        CreateProcessW(
            lpApplicationName = null,
            lpCommandLine = cmdLine.wcstr.ptr,
            lpProcessAttributes = null,
            lpThreadAttributes = null,
            bInheritHandles = TRUE,
            dwCreationFlags = CREATE_NO_WINDOW.toUInt(),
            lpEnvironment = buildEnvBlock(),
            lpCurrentDirectory = workDir,
            lpStartupInfo = si.ptr,
            lpProcessInformation = pi.ptr,
        ).also { check(it != 0) { "CreateProcess failed: ${GetLastError()}" } }

        CloseHandle(sir.value)
        CloseHandle(sow.value)
        if (!mergeErr) CloseHandle(sew.value)

        Process(
            hProcess = pi.hProcess!!,
            hThread = pi.hThread!!,
            pid = pi.dwProcessId.toLong(),
            stdinHandle = siw.value!!,
            stdoutHandle = sor.value!!,
            stderrHandle = if (mergeErr) sor.value!! else ser.value!!,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun MemScope.buildEnvBlock(): CPointer<WCHARVar>? {
        if (extraEnv.isEmpty()) return null
        val sb = StringBuilder()
        GetEnvironmentStringsW()?.let { ptr ->
            var i = 0
            while (true) {
                val start = i
                while (ptr[i] != 0.toUShort()) i++
                if (i == start) break
                @Suppress("EmptyRange")
                val entry = (start until i).map { ptr[it].toInt().toChar() }.joinToString("")
                if (entry.substringBefore('=') !in extraEnv) sb.append(entry).append('\u0000')
                i++
            }
            FreeEnvironmentStringsW(ptr)
        }
        extraEnv.forEach { (k, v) -> sb.append("$k=$v").append('\u0000') }
        sb.append('\u0000')
        return sb.toString().wcstr.ptr
    }

    private fun escapeArg(arg: String): String {
        if (!arg.contains(Regex("""[ \t"\\]"""))) return arg
        val sb = StringBuilder("\"")
        var slashes = 0
        for (c in arg) {
            when (c) {
                '\\' -> slashes++

                '"' -> {
                    repeat(slashes * 2 + 1) { sb.append('\\') }
                    slashes = 0
                    sb.append('"')
                }

                else -> {
                    repeat(slashes) { sb.append('\\') }
                    slashes = 0
                    sb.append(c)
                }
            }
        }
        repeat(slashes * 2) { sb.append('\\') }
        return sb.append('"').toString()
    }
}
