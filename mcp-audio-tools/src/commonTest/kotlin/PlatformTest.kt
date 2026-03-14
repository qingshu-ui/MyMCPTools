package io.github.qingshu.mcpaudiotools

import kotlinx.coroutines.runBlocking
import kotlinx.io.writeString
import kotlin.test.Test
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

    @Test
    fun `runProcess streamable output should not throw exception`(): Unit = runBlocking {
        val file = "d:\\Users\\17186\\Downloads\\Music\\事先准备.wav"

        runProcess(
            "ffmpeg",
            "-hide_banner",
            "-nostats",
            "-progress pipe:1",
            "-stats_period 10",
            "-y",
            "-i $file",
            "-codec:a libmp3lame",
            "-qscale:a 2",
            "$file.mp3",
            onProgress = ::print,
        )
    }

    @Test
    fun `platformProcess should not threw exception`() {
        val system = platformProcess()

        system.output.writeString("Hello world")
        system.output.flush()
    }
}
