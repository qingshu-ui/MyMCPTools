package io.github.qingshu.process

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessTest {

    @Test
    fun `exit code should be 0 for successful command`(): Unit = runBlocking {
        val exitCode = ProcessBuilder("bash", "-c", "echo hello").start().awaitExit()
        assertEquals(0, exitCode)
    }

    @Test
    fun `exec result stdout should not be empty`(): Unit = runBlocking {
        val result = Process.exec("bash", "-c", "echo Hello World")
        assertTrue { result.stdout.isNotEmpty() }
    }

    @Test
    fun `exec should capture stdout`(): Unit = runBlocking {
        val result = Process.exec("bash", "-c", "echo Hello World")
        assertEquals("Hello World", result.stdout.trim())
    }

    @Test
    fun `exec should return non-zero exit code on failure`(): Unit = runBlocking {
        val result = Process.exec("bash", "-c", "exit 1")
        assertEquals(1, result.code)
    }
}
