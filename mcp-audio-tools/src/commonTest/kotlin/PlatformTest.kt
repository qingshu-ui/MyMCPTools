package io.github.qingshu.mcpaudiotools

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun `runProcess result isSuccess should be true`() {
        val result = runProcess(
            "ffmpeg",
            "-version",
        )
        println("$result")
        assertTrue(result.isSuccess)
    }
}
