package io.github.qingshu.essentialmcp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.fflush
import platform.posix.read
import platform.posix.stdout
import platform.posix.write

actual fun stdin() = object : RawSource {
    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val bytes = ByteArray(byteCount.toInt())
        val read = bytes.usePinned { pinned ->
            read(STDIN_FILENO, pinned.addressOf(0), byteCount.toULong())
        }
        return if (read <= 0L) {
            -1L
        } else {
            sink.write(bytes, 0, read.toInt())
            read
        }
    }

    override fun close() {
    }
}

actual fun stdout() = object : RawSink {
    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
    override fun write(source: Buffer, byteCount: Long) {
        val bytes = source.readByteArray(byteCount.toInt())
        bytes.usePinned { pinned ->
            write(STDOUT_FILENO, pinned.addressOf(0), byteCount.toULong())
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun flush() {
        fflush(stdout)
    }

    override fun close() {
    }
}
