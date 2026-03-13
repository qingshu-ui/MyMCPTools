package io.github.qingshu.mcpaudiotools

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

@OptIn(ExperimentalForeignApi::class)
actual fun runProcess(vararg args: String): ProcessResult {
    val cmd = args.joinToString(" ") + " 2>&1"

    val output = StringBuilder()
    val fp = popen(cmd, "r")
        ?: return ProcessResult(-1, "cmd execute filed")

    memScoped {
        val buf = allocArray<ByteVar>(4096)
        while (fgets(buf, 4096, fp) != null) {
            output.append(buf.toKString())
        }
    }

    val exit = pclose(fp)
    return ProcessResult(exit, output.toString())
}
