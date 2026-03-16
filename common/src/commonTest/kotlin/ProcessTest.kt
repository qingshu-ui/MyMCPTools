package io.github.qingshu.mcptool.common

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessTest {

    @Test
    fun `when ffmpeg exists exit code should be 0`(): Unit = runBlocking {
        // val result = exec("ffmpeg", "-version")
        val builder = ProcessBuilder("ffmpeg", "-version")
        val exitCode = builder.start().waitFor()
        println("$exitCode")
        assertEquals(0, exitCode)
    }

    @Test
    fun `exec result stdout should not empty`(): Unit = runBlocking {
        val result = Process.exec("bash", "-c", "ls -l")
        println(result)
        assertTrue { result.stdout.isNotEmpty() }
    }

    @Test
    fun `test long command execute`(): Unit = runBlocking {
        val paths = "find " +
            "/mnt/d/Users/17186/Downloads/Music/RJ01126901 " +
            "/mnt/d/Users/17186/Downloads/Music/RJ01151475 " +
            "/mnt/d/Users/17186/Downloads/Music/RJ01179078 -type f"

        val result = Process.exec(
            "bash",
            "-c",
            paths,
        )
        println(result)
    }
}
