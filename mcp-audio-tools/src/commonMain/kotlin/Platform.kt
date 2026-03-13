package io.github.qingshu.mcpaudiotools

expect fun platformProcess(): Process

expect fun runProcess(vararg args: String): ProcessResult
