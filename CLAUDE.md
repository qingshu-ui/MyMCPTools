# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Kotlin Multiplatform MCP (Model Context Protocol) Server** that provides audio processing, image understanding, and command execution capabilities as MCP tools. The project implements an MCP server that can be used with Claude Desktop and other MCP clients.

**Core functionality:**
- MCP server running over stdio transport
- Audio transcoding (WAV to MP3 using ffmpeg)
- Subtitle conversion (SRT/VTT to LRC format)
- Generic command execution
- Image understanding via OpenAI-compatible vision API

## Architecture

### Module Structure
The project uses Kotlin Multiplatform with four modules:

1. **mcp-tool-annotations**: Multiplatform annotation library (`@McpTool`, `@ToolParam`, `@McpResource`, `@McpPrompt`, `@PromptParam`)
   - Used to annotate MCP tool functions and their parameters
   - Processed by KSP to generate tool registration code

2. **mcp-tool-ksp**: KSP processor (JVM only)
   - Generates `GeneratedMcpTools.kt` with tool metadata and registration
   - Generates `GeneratedMcpToolsCompileTest.kt` for compilation verification
   - Outputs to `build/generated/ksp/metadata/commonMain/kotlin/`

3. **process**: Shared process abstraction library
   - Expect/actual pattern for Process/ProcessBuilder
   - Platform implementations: JVM, Linux (uses FDs), Windows (uses handles)
   - Utility functions: `awaitExit()`, `stdoutLines()`, `stderrLines()`, `exec()`

4. **essential-mcp**: Main MCP server application
   - Depends on all other modules
   - Contains tool implementations and server entry point

### Platform Targets
- **JVM**: For desktop/CLI usage (main development target)
- **Linux** (x64, arm64): Native executables via Kotlin/Native
- **Windows** (mingwX64): Native executables via Kotlin/Native

### Key Components

**MCP Server** (`essential-mcp/src/commonMain/kotlin/`):
- `Main.kt` - Entry point with server setup and stdio transport
- `Server.kt` - MCP server factory
- `Platform.kt` - Platform-specific expect declarations (including `httpClientEngine`)
- `Process.kt` - Stdio transport Process interface
- `ProcessResult.kt` - Process result types

**MCP Declarations** (`essential-mcp/src/commonMain/kotlin/mcptool/`):
- Annotated with `@McpTool`, `@McpResource`, `@McpPrompt` from `mcp-tool-annotations`
- `TranscodeWavToMp3.kt` - WAV to MP3 transcoding using ffmpeg (`@McpTool`, deprecated)
- `SubtitleToLrc.kt` - Subtitle format conversion (`@McpTool`, deprecated, requires external `subtitle_to_lrc` binary)
- `ExecuteCommand.kt` - Generic shell command execution (`@McpTool`)
- `UnderstandImage.kt` - Image understanding via vision API (`@McpTool`, requires `VISION_API_KEY` env var)
- `AudioResources.kt` - MCP resource definitions (`@McpResource`)
- `AudioPrompts.kt` - MCP prompt definitions (`@McpPrompt`)

**KSP Code Generation**:
- Annotations defined in `mcp-tool-annotations/src/commonMain/kotlin/`
- Processor in `mcp-tool-ksp/src/main/kotlin/`
- Generated code: `io.github.qingshu.essentialmcp.mcptool.GeneratedMcpTools`
- Build wiring: KSP runs before compilation (see `essential-mcp/build.gradle.kts` lines 69-78)

**MCP Server Process** (`essential-mcp/src/commonMain/kotlin/Process.kt`):
- Separate `Process` interface for stdio transport (input: Source, output: Sink)
- Used to connect MCP server to stdin/stdout for stdio transport
- Different from the subprocess `Process` in the `process` module

## Development Commands

### Build
```bash
./gradlew build              # Build all modules, run tests
./gradlew assemble           # Build artifacts without testing
./gradlew :essential-mcp:jvmJar  # Build JVM JAR only
./gradlew :essential-mcp:fatJar  # Build fat/uber JAR
```

### Native Binaries
```bash
./gradlew :essential-mcp:linkReleaseExecutableLinuxX64   # Linux x64
./gradlew :essential-mcp:linkReleaseExecutableLinuxArm64 # Linux ARM64
./gradlew :essential-mcp:linkReleaseExecutableMingwX64   # Windows
```

### Testing
```bash
./gradlew test               # Run all tests (common + platform-specific)
./gradlew :essential-mcp:jvmTest  # Run JVM tests only
./gradlew :mcp-tool-ksp:test        # Run KSP processor tests
./gradlew test --tests "*PlatformTest"  # Run specific test class
```

### Linting & Formatting
```bash
./gradlew spotlessApply      # Format all code (Kotlin, Gradle KTS)
./gradlew spotlessCheck      # Check formatting without changes
```

### KSP Code Generation
```bash
./gradlew :essential-mcp:kspCommonMainKotlinMetadata  # Run KSP manually
```
The KSP task is automatically wired to run before compilation tasks (see `essential-mcp/build.gradle.kts`).

### Running the Server
```bash
# JVM (development):
java -jar essential-mcp/build/libs/essential-mcp-jvm-1.1.1.jar

# Or run directly:
./gradlew :essential-mcp:jvmRun

# Native (after linking):
./essential-mcp/build/bin/linuxX64/executable/essential-mcp-1.1.1
```

## Code Conventions

### Process Handling
- Use coroutines for async I/O: `launch { process.stdoutLines().collect { ... } }`
- Always redirect/merge stderr when capturing output
- Use `awaitExit()` for clean shutdown
- Create output directories with `SystemFileSystem.createDirectories(Path(output))`

### MCP Declarations
- Annotate tool functions with `@McpTool` and schema parameters with `@ToolParam`
- Annotate resource functions with `@McpResource`
- Annotate prompt functions with `@McpPrompt` and schema parameters with `@PromptParam`
- Unannotated parameters with SDK context types (`CallToolRequest`, `ClientConnection`, `Server` for tools; `ReadResourceRequest`, `ClientConnection`, `Server` for resources; `GetPromptRequest`, `ClientConnection`, `Server` for prompts) are automatically injected at the call site in generated code
- Functions return `String` — KSP generates the wrapper that produces `CallToolResult`
- Use `[OK]`/`[Failed]` prefixes for tool output messages
- KSP auto-generates registration code in `GeneratedMcpTools`

### Platform-Specific Code
- Use `expect`/`actual` declarations in `essential-mcp/src/commonMain/kotlin/Platform.kt`
- Platform implementations in: `jvmMain`, `linuxMain`, `mingwMain`, `nativeMain`
- For essential-mcp platform code: `essential-mcp/src/{jvm,linux,mingw,native}Main/kotlin/`
- For process library: `process/src/{jvm,linux,mingw}Main/kotlin/`

## Dependencies

Core libraries (from `gradle/libs.versions.toml`):
- Kotlin 2.3.21
- kotlinx.coroutines 1.10.2
- kotlinx.serialization 1.9.0
- kotlinx.io 0.9.0
- Model Context Protocol SDK 0.12.0
- KSP 2.3.7
- KotlinPoet 2.2.0
- Ktor 3.4.1

## Notes

- The project includes an Aliyun Maven mirror for dependency resolution (configured in `settings.gradle.kts`)
- CI environment uses official Gradle distribution; local uses mirrored distribution
- All code is formatted with Spotless using ktlint with custom rules
- Tests include platform-specific process handling tests; may require actual binaries (ffmpeg) on the target platform
- The `subtitle_to_lrc` tool expects an external binary in PATH or configured via `SUBTITLE_TO_LRC` env var
- The `understand_image` tool requires `VISION_API_KEY` env var; optional `VISION_API_URL` and `VISION_MODEL` (defaults in `Constants.kt`)
- The `execute_command` tool defaults to `bash`; override with `SHELL_PATH` env var (e.g. Git Bash on Windows)
- `transcode_wav_to_mp3` and `subtitle_to_lrc` are deprecated and will be removed in the next major version
- The `process` module is published as a separate library (group: `io.github.qingshu-ui`, name: `process`)
- KSP-generated sources are in `build/generated/ksp/metadata/commonMain/kotlin/` and wired into the compilation

## File Locations (Quick Reference)

- Build config: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`
- Gradle wrapper: `gradle/wrapper/`
- Annotations: `mcp-tool-annotations/src/commonMain/kotlin/`
- KSP processor: `mcp-tool-ksp/src/main/kotlin/`
- Process library: `process/src/{jvm,linux,mingw}Main/kotlin/` (package: `io.github.qingshu.process`)
- MCP server: `essential-mcp/src/commonMain/kotlin/`
- MCP declarations (tools/resources/prompts): `essential-mcp/src/commonMain/kotlin/mcptool/`
- Generated code: `essential-mcp/build/generated/ksp/metadata/commonMain/kotlin/`
- Tests: `**/src/**/test/kotlin/`
