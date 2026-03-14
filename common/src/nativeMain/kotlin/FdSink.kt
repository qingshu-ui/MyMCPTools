package io.github.qingshu.mcptool.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.readTo
import platform.posix.close
import platform.posix.errno
import platform.posix.strerror
import platform.posix.write

class FdSink(private val fd: Int) : RawSink {
    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    override fun write(source: Buffer, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val chunk = ByteArray(remaining.coerceAtMost(8192).toInt())
            source.readTo(chunk, endIndex = chunk.size)
            chunk.usePinned { pinned ->
                var written = 0
                while (written < chunk.size) {
                    val n = write(
                        fd,
                        pinned.addressOf(written),
                        (chunk.size - written).toUInt(),
                    )
                    if (n <= 0) throw IOException("write() failed: ${strerror(errno)?.toKString()}")
                    written += n
                }
            }
            remaining -= chunk.size
        }
    }

    override fun flush() {
    }

    override fun close() {
        close(fd)
    }
}
