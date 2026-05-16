# Windows Compatibility Fixes for execute_command and understand_image

## Problems

1. The `execute_command` MCP tool hardcodes `bash` as the shell (`ProcessBuilder("bash", "-c", cmd)`). On Windows, `bash` resolves to WSL's bash, causing path issues (WSL paths vs Windows paths). Users who have Git Bash installed want to use it instead.

2. The `understand_image` tool calls `runProcess("base64", image)` to encode local files as base64. The `base64` command is not available on Windows by default, causing the tool to fail for local file paths.

## Design

### Fix 1: SHELL_PATH env var for execute_command

Add a `SHELL_PATH` environment variable that controls which shell executable `execute_command` uses. If not set, the default remains `"bash"`.

#### Changes

1. **`Constants.kt`** — Add constant `SHELL_PATH = "SHELL_PATH"` (consistent with existing env var constants like `SUBTITLE_TO_LRC`, `VISION_API_KEY`).

2. **`ExecuteCommand.kt`** — Read `SHELL_PATH` via `getEnv(SHELL_PATH)`, use it or default to `"bash"`:
   ```kotlin
   val shell = getEnv(SHELL_PATH) ?: "bash"
   val process = ProcessBuilder(shell, "-c", cmd).run {
       cwd?.let(::directory)
       start()
   }
   ```

No platform code changes needed — `getEnv()` already exists as an `expect`/`actual` function on all platforms.

#### Usage

- Set `SHELL_PATH=C:\Program Files\Git\bin\bash.exe` to use Git Bash on Windows
- Set `SHELL_PATH=/bin/zsh` to use zsh on any platform
- If not set, defaults to `"bash"` (current behavior)

### Fix 2: Replace base64 subprocess with Kotlin API in understand_image

Replace `runProcess("base64", image)` with direct file reading via `kotlinx-io` and base64 encoding via `kotlinx.serialization`. This removes the dependency on the external `base64` command and works cross-platform.

#### Changes

1. **`UnderstandImage.kt`** — Replace the `else` branch in `imageUrl` construction:
   - Read file bytes using `SystemFileSystem.read(Path(image))`
   - Encode to base64 using `Base64.encode()` from `kotlinx.io`
   - Remove the `runProcess` import

#### Before
```kotlin
val result = runProcess("base64", image)
if (!result.isSuccess) {
    return "[Failed] Failed to read file '$image': ${result.output}"
}
val mimeType = inferMimeType(image)
"data:$mimeType;base64,${result.output.trim()}"
```

#### After
```kotlin
val bytes = SystemFileSystem.read(Path(image)) { readByteArray() }
val mimeType = inferMimeType(image)
"data:$mimeType;base64,${Base64.encode(bytes)}"
```

Uses `kotlin.io.encoding.Base64` from the Kotlin standard library (available since Kotlin 2.0) and `kotlinx.io.files.SystemFileSystem` / `Path` from the existing kotlinx-io dependency.
