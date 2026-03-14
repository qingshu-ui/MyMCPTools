package io.github.qingshu.mcpaudiotools

import io.github.qingshu.mcptool.common.Process
import io.github.qingshu.mcptool.common.exec
import kotlinx.coroutines.runBlocking
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun `runProcess result isSuccess should be true`(): Unit = runBlocking {
        val result = runProcess(
            "ffmpeg",
            "-version",
        )
        println("$result")
        assertTrue(result.isSuccess)
    }

    val file = "d:\\Users\\17186\\Downloads\\Music\\事先准备.wav"
    private val cmd = arrayOf(
        "ffmpeg",
        "-hide_banner",
        "-nostats",
        "-y",
        "-i",
        file,
        "-codec:a",
        "libmp3lame",
        "-qscale:a",
        "2",
        "$file.mp3",
    )

    @Test
    fun `runProcess should not threw exception`(): Unit = runBlocking {
        val result = runProcess(*cmd)
        println(result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ffmpeg test using common process`(): Unit = runBlocking {
        val result = Process.exec(*cmd)
        println("$result")
        assertEquals(0, result.code)
    }

    @Test
    fun `platformProcess should not threw exception`() {
        val system = platformProcess()

        system.output.writeString("Hello world")
        system.output.flush()
    }
}
