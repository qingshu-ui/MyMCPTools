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

/**
 * Represents an operating system process.
 * This is an expect class that is platform-specific in actual implementations.
 */
expect class Process {
    /** The process ID. */
    val pid: Long
    /** The standard input stream of the process. */
    val stdin: RawSink
    /** The standard output stream of the process. */
    val stdout: RawSource
    /** The standard error stream of the process. */
    val stderr: RawSource
    /**
     * Waits for the process to terminate and returns its exit code.
     * @return the exit code of the process
     */
    fun waitFor(): Int
    /**
     * Returns the exit code of the process if it has terminated, or null if it is still running.
     * @return the exit code, or null if the process is still running
     */
    fun exitCode(): Int?
    /**
     * Attempts to terminate the process gracefully.
     */
    fun destroy()
    /**
     * Attempts to terminate the process forcefully.
     */
    fun destroyForcibly()

    companion object
}

/**
 * Waits for the process to complete asynchronously.
 * This is a suspending function that should be called from a coroutine.
 */
suspend fun Process.awaitExit() = withContext(Dispatchers.IO) { waitFor() }

/**
 * Returns a flow of lines read from the process's standard output.
 * Each line is emitted as it becomes available.
 * The flow is collected on the IO dispatcher.
 */
fun Process.stdoutLines(): Flow<String> = flow {
    val buf = stdout.buffered()
    try {
        while (true) emit(buf.readLine() ?: break)
    } finally {
        buf.close()
    }
}.flowOn(Dispatchers.IO)

/**
 * Returns a flow of lines read from the process's standard error.
 * Each line is emitted as it becomes available.
 * The flow is collected on the IO dispatcher.
 */
fun Process.stderrLines(): Flow<String> = flow {
    val buf = stderr.buffered()
    try {
        while (true) emit(buf.readLine() ?: break)
    } finally {
        buf.close()
    }
}.flowOn(Dispatchers.IO)

/**
 * Executes a command as a subprocess and collects its output.
 * @param command the command and its arguments to execute
 * @param workDir the working directory for the process (optional)
 * @return a ProcessResult containing the exit code, stdout, and stderr
 */
suspend fun Process.Companion.exec(vararg command: String, workDir: String? = null): ProcessResult {
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
