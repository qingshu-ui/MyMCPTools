package io.github.qingshu.essentialmcp

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

@OptIn(ExperimentalForeignApi::class)
@Deprecated("Will be removed in the next major version.", level = DeprecationLevel.WARNING)
actual suspend fun runProcess(vararg args: String): ProcessResult = withContext(Dispatchers.Default) {
    val cmd = args.joinToString(" ") + " 2>&1"

    val output = StringBuilder()
    val fp = popen(cmd, "r")
        ?: return@withContext ProcessResult(-1, "cmd execute filed")

    memScoped {
        val buf = allocArray<ByteVar>(4096)
        while (fgets(buf, 4096, fp) != null) {
            val line = buf.toKString()
            output.append(line)
        }
    }

    val exit = pclose(fp)
    ProcessResult(exit, output.toString())
}
