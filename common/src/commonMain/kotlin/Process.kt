package io.github.qingshu.mcptool.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readLine

expect class Process {
    val pid: Long
    val stdin: RawSink
    val stdout: RawSource
    val stderr: RawSource
    fun waitFor(): Int
    fun exitCode(): Int?
    fun destroy()
    fun destroyForcibly()
}

suspend fun Process.awaitExit() = withContext(Dispatchers.IO) { waitFor() }

fun Process.stdoutLines(): Flow<String> = flow {
    val buf = stdout.buffered()
    try {
        while (true) emit(buf.readLine() ?: break)
    } finally {
        buf.close()
    }
}.flowOn(Dispatchers.IO)

fun Process.stderrLines(): Flow<String> = flow {
    val buf = stderr.buffered()
    try {
        while (true) emit(buf.readLine() ?: break)
    } finally {
        buf.close()
    }
}.flowOn(Dispatchers.IO)

internal suspend fun exec(vararg command: String, workDir: String? = null): ProcessResult {
    val proc = ProcessBuilder(*command)
        .apply { workDir?.let { directory(it) } }
        .start()
    val out = StringBuilder()
    val err = StringBuilder()
    coroutineScope {
        launch { proc.stdoutLines().collect { out.appendLine(it) } }
        launch { proc.stderrLines().collect { err.appendLine(it) } }
    }
    return ProcessResult(proc.awaitExit(), out.toString(), err.toString())
}
