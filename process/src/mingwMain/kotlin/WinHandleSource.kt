package io.github.qingshu.process

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import platform.windows.CloseHandle
import platform.windows.DWORDVar
import platform.windows.HANDLE
import platform.windows.ReadFile

@OptIn(ExperimentalForeignApi::class)
class WinHandleSource(private val handle: HANDLE) : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val buf = ByteArray(byteCount.coerceAtMost(8192).toInt())
        return buf.usePinned { pinned ->
            memScoped {
                val read = alloc<DWORDVar>()
                val ok = ReadFile(handle, pinned.addressOf(0), buf.size.toUInt(), read.ptr, null)
                if (ok == 0 || read.value == 0u) {
                    -1L
                } else {
                    sink.write(buf, 0, read.value.toInt())
                    read.value.toLong()
                }
            }
        }
    }

    override fun close() {
        CloseHandle(handle)
    }
}
