package io.github.qingshu.mcpaudiotools

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

actual fun runProcess(vararg args: String): ProcessResult {
    val process = ProcessBuilder(*args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    return ProcessResult(process.waitFor(), output)
}
