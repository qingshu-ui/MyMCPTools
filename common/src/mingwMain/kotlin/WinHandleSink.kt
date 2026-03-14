package io.github.qingshu.mcptool.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.readTo
import platform.windows.CloseHandle
import platform.windows.DWORDVar
import platform.windows.HANDLE
import platform.windows.WriteFile

@OptIn(ExperimentalForeignApi::class)
class WinHandleSink(private val handle: HANDLE) : RawSink {
    override fun write(source: Buffer, byteCount: Long) {
        val buf = ByteArray(byteCount.toInt())
        source.readTo(buf)
        buf.usePinned { pinned ->
            memScoped {
                val written = alloc<DWORDVar>()
                WriteFile(handle, pinned.addressOf(0), buf.size.toUInt(), written.ptr, null)
            }
        }
    }

    override fun flush() {
    }

    override fun close() {
        CloseHandle(handle)
    }
}
