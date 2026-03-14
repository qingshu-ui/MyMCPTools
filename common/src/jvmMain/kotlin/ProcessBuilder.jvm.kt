package io.github.qingshu.mcptool.common

import java.io.File

actual class ProcessBuilder actual constructor(vararg command: String) {
    private val pb = java.lang.ProcessBuilder(*command)

    actual fun directory(dir: String): ProcessBuilder = apply {
        pb.directory(File(dir))
    }

    actual fun mergeStderr(merge: Boolean): ProcessBuilder = apply {
        pb.redirectErrorStream(merge)
    }

    actual fun environment(key: String, value: String): ProcessBuilder = apply {
        pb.environment()[key] = value
    }

    actual fun start(): Process = Process(pb.start())
}