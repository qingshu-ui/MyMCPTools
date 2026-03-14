package io.github.qingshu.mcptool.common

expect class ProcessBuilder(vararg command: String) {
    fun directory(dir: String): ProcessBuilder
    fun mergeStderr(merge: Boolean): ProcessBuilder
    fun environment(key: String, value: String): ProcessBuilder
    fun start(): Process
}
