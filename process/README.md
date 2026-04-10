# process

A Kotlin Multiplatform process abstraction library providing `Process` and `ProcessBuilder` expect/actual implementations for JVM, Linux (x64/arm64), and Windows (MinGW).

## Features

- **Cross-platform process execution** via `ProcessBuilder` and `Process`
- **Async I/O** using Kotlin Coroutines and flows
- **Expect/actual pattern** — shared API in `commonMain`, platform-specific implementations in `jvmMain`, `linuxMain`, and `mingwMain`

## Supported Platforms

| Platform | Implementation |
|----------|---------------|
| JVM | `java.lang.Process` backed |
| Linux (x64, arm64) | POSIX file descriptor backed |
| Windows (MinGW) | Windows `HANDLE` backed |

## Usage

```kotlin
import io.github.qingshu.process.ProcessBuilder
import io.github.qingshu.process.Process
import io.github.qingshu.process.ProcessResult
import io.github.qingshu.process.awaitExit
import io.github.qingshu.process.exec
import io.github.qingshu.process.stdoutLines
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

fun main() {
    // Simple one-liner with exec()
    val result: ProcessResult = runBlocking {
        Process.exec("ffmpeg", "-version")
    }
    println("exit code: ${result.code}")

    // With working directory
    val result2: ProcessResult = runBlocking {
        Process.exec("ls", "-la", workDir = "/tmp")
    }

    // Stream output with flows
    val process = ProcessBuilder("ls", "-la").start()
    process.stdoutLines().onEach { line -> println(line) }.join()
    process.awaitExit()

    // Fine-grained control with ProcessBuilder
    val proc = ProcessBuilder("ffmpeg", "-version")
        .directory("/tmp")
        .mergeStderr(true)
        .environment("MY_VAR", "value")
        .start()

    val exitCode = proc.awaitExit()
    proc.destroy()
}
```

## License

GNU Affero General Public License v3 (AGPLv3)
