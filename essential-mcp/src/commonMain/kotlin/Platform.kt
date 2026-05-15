package io.github.qingshu.essentialmcp

import io.ktor.client.engine.HttpClientEngine

expect fun platformProcess(): Process

expect suspend fun runProcess(vararg args: String): ProcessResult

expect fun disableKotlinLogging()

expect fun getEnv(key: String): String?

expect val httpClientEngine: HttpClientEngine
