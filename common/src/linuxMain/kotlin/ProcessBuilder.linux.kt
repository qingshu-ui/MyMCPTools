@file:Suppress("SpellCheckingInspection")

package io.github.qingshu.mcptool.common

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.X_OK
import platform.posix.__environ
import platform.posix._exit
import platform.posix.access
import platform.posix.chdir
import platform.posix.close
import platform.posix.dup2
import platform.posix.errno
import platform.posix.execve
import platform.posix.fork
import platform.posix.pipe
import platform.posix.strerror

actual class ProcessBuilder actual constructor(vararg command: String) {
    private val cmd = command.toList()
    private var workDir: String? = null
    private var mergeErr = false
    private val env = mutableMapOf<String, String>()

    actual fun directory(dir: String): ProcessBuilder = apply { workDir = dir }

    actual fun mergeStderr(merge: Boolean): ProcessBuilder = apply { mergeErr = merge }

    actual fun environment(key: String, value: String): ProcessBuilder = apply { env[key] = value }

    @OptIn(ExperimentalForeignApi::class)
    actual fun start(): Process = memScoped {
        val si = IntArray(2)
        val so = IntArray(2)
        val se = IntArray(2)
        pipe(si.refTo(0))
        pipe(so.refTo(0))
        pipe(se.refTo(0))

        val childPid = fork()
        check(childPid >= 0) { "fork() failed: ${strerror(errno)?.toKString()}" }

        if (childPid == 0) {
            close(si[1])
            close(so[0])
            dup2(si[0], STDIN_FILENO)
            dup2(so[1], STDOUT_FILENO)

            if (mergeErr) {
                dup2(so[1], STDERR_FILENO)
            } else {
                close(se[0])
                dup2(se[1], STDERR_FILENO)
            }
            close(si[0])
            close(so[1])
            close(se[1])

            workDir?.let { chdir(it) }

            val argv = allocArrayOf(*cmd.map { it.cstr.ptr }.toTypedArray(), null)
            val (envp, envMap) = buildEnvp()
            val exe = findExecutble(cmd[0], envMap)
            execve(exe, argv, envp)
            _exit(127)
        }

        close(si[0])
        close(so[1])
        close(se[1])

        Process(
            pid = childPid.toLong(),
            stdinFd = si[1],
            stdoutFd = so[0],
            stderrFd = if (mergeErr) so[0] else se[0],
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun MemScope.buildEnvp(): Pair<CValuesRef<CPointerVar<ByteVar>>, Map<String, String>> {
        val base = mutableMapOf<String, String>()
        var i = 0
        while (true) {
            val s = __environ?.get(i++)?.toKString() ?: break
            val eq = s.indexOf('=')
            if (eq > 0) base[s.substring(0, eq)] = s.substring(eq + 1)
        }
        base.putAll(env)
        val ptr = allocArrayOf(*base.entries.map { (k, v) -> "$k=$v".cstr.ptr }.toTypedArray(), null)
        return ptr to base
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun findExecutble(name: String, env: Map<String, String>): String {
        if ('/' in name) return name

        val path = env["PATH"] ?: "/usr/local/bin:/usr/bin:/bin"
        return path.split(':')
            .map { "$it/$name" }
            .firstOrNull { access(it, X_OK) == 0 }
            ?: name
    }
}
