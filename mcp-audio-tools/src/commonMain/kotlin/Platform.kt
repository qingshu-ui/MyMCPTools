package io.github.qingshu.mcpaudiotools

expect fun platformProcess(): Process

expect suspend fun runProcess(vararg args: String): ProcessResult

expect fun disableKotlinLogging()
