package io.github.qingshu.mcptool.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import platform.posix.close
import platform.posix.errno
import platform.posix.read
import platform.posix.strerror

class FdSource(private val fd: Int) : RawSource {
    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val buf = ByteArray(byteCount.coerceAtMost(8192).toInt())
        val n = buf.usePinned { pinned ->
            read(fd, pinned.addressOf(0), buf.size.convert())
        }
        val size: Int = n.convert()
        return when {
            size < 0 -> throw IOException("read() failed: ${strerror(errno)?.toKString()}")

            size == 0 -> -1L

            else -> {
                sink.write(buf, 0, n.convert())
                n.convert()
            }
        }
    }

    override fun close() {
        close(fd)
    }
}
