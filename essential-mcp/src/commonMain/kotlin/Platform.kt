package io.github.qingshu.essentialmcp

expect fun platformProcess(): Process

@Deprecated("Will be removed in the next major version.", level = DeprecationLevel.WARNING)
expect suspend fun runProcess(vararg args: String): ProcessResult

expect fun disableKotlinLogging()

expect fun getEnv(key: String): String?
