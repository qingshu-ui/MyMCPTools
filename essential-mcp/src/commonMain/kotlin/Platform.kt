package io.github.qingshu.essentialmcp

expect fun platformProcess(): Process

expect suspend fun runProcess(vararg args: String): ProcessResult

expect fun disableKotlinLogging()

expect fun getEnv(key: String): String?
