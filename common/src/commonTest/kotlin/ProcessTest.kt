package io.github.qingshu.mcptool.common

import kotlin.test.Test

class ProcessTest {

    @Test
    fun hello() {

        val process = ProcessBuilder(
            "ls",
            "-l"
        )

        val result = process.start()
        println(result)
    }
}