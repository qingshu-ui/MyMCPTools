package io.github.qingshu.process

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessTestMingwTest {

    @Test
    fun `test set environment variable`(): Unit = runBlocking {
        val binPath = "d:\\Users\\17186\\Downloads\\Compressed\\subtitle-to-lrc_windows-amd64\\subtitle-to-lrc.exe"
        val process = ProcessBuilder("cmd", "/c", "%SUBTITLE_TO_LRC%", "-h")
            .environment("SUBTITLE_TO_LRC", binPath)
            .mergeStderr(true)
            .start()

        launch {
            process.stdoutLines().collect(::println)
        }
        assertEquals(0, process.waitFor())
    }
}
