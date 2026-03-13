package io.github.qingshu.mcpaudiotools

import kotlinx.io.Sink
import kotlinx.io.Source

interface Process {
    val input: Source
    val output: Sink
}
