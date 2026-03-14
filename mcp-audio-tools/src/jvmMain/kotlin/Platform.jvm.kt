package io.github.qingshu.mcpaudiotools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

class JvmProcess : Process {
    override val input: Source = System.`in`.asSource().buffered()
    override val output: Sink = System.out.asSink().buffered()
}

actual fun platformProcess(): Process = JvmProcess()

actual suspend fun runProcess(vararg args: String): ProcessResult = withContext(Dispatchers.IO) {
    val process = withContext(Dispatchers.IO) {
        ProcessBuilder(*args)
            .redirectErrorStream(true)
            .start()
    }

    val output = StringBuilder()
    val readerJob = launch {
        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                output.appendLine(line)
            }
        }
    }
    val exitCode = process.waitFor()
    readerJob.join()

    ProcessResult(exitCode, output.toString())
}
