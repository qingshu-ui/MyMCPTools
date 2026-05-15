package io.github.qingshu.essentialmcp

import kotlinx.io.Sink
import kotlinx.io.Source

interface Process {
    val input: Source
    val output: Sink
}
