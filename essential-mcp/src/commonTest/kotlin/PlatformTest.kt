package io.github.qingshu.essentialmcp

import io.github.qingshu.process.Process
import io.github.qingshu.process.exec
import kotlinx.coroutines.runBlocking
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun `runProcess should succeed for simple command`(): Unit = runBlocking {
        val result = runProcess("bash", "-c", "echo hello")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `Process exec should capture stdout`(): Unit = runBlocking {
        val result = Process.exec("bash", "-c", "echo Hello World")
        assertEquals(0, result.code)
        assertEquals("Hello World", result.stdout.trim())
    }

    @Test
    fun `platformProcess should not throw exception`() {
        val system = platformProcess()

        system.output.writeString("Hello world")
        system.output.flush()
    }
}
