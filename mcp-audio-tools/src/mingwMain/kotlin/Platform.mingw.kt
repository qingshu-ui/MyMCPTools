package io.github.qingshu.mcpaudiotools

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix._pclose
import platform.posix._popen
import platform.posix.fgets

@OptIn(ExperimentalForeignApi::class)
actual suspend fun runProcess(vararg args: String, onProgress: suspend (String) -> Unit): ProcessResult = withContext(Dispatchers.Default) {
    val cmd = args.joinToString(" ") + " 2>&1"

    val output = StringBuilder()
    val fp = _popen(cmd, "r")
        ?: return@withContext ProcessResult(-1, "cmd execute filed")

    memScoped {
        val buf = allocArray<ByteVar>(4096)
        while (fgets(buf, 4096, fp) != null) {
            val line = buf.toKString()
            output.append(line)
            onProgress(line)
        }
    }

    val exit = _pclose(fp)
    ProcessResult(exit, output.toString())
}
