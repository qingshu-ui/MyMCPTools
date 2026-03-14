package io.github.qingshu.mcptool.common

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ProcessTest {

    @Test
    fun `when ffmpeg exists exit code should be 0`(): Unit = runBlocking {
        // val result = exec("ffmpeg", "-version")
        val builder = ProcessBuilder("ffmpeg", "-version")
        val exitCode = builder.start().waitFor()
        println("$exitCode")
    }
}