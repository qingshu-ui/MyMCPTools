# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Kotlin Multiplatform MCP (Model Context Protocol) Audio Tools Server** that provides audio processing capabilities as MCP tools. The project implements an MCP server that can be used with Claude Desktop and other MCP clients.

**Core functionality:**
- MCP server running over stdio transport
- Audio transcoding (WAV to MP3 using ffmpeg)
- Subtitle conversion (SRT/VTT to LRC format)
- Generic command execution

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

4. **mcp-audio-tools**: Main MCP server application
   - Depends on all other modules
   - Contains tool implementations and server entry point

### Platform Targets
- **JVM**: For desktop/CLI usage (main development target)
- **Linux** (x64, arm64): Native executables via Kotlin/Native
- **Windows** (mingwX64): Native executables via Kotlin/Native

### Key Components

**MCP Server** (`mcp-audio-tools/src/commonMain/kotlin/`):
- `Main.kt` - Entry point with server setup and stdio transport
- `Server.kt` - MCP server factory
- `Platform.kt` - Platform-specific expect declarations
- `Process.kt` - Stdio transport Process interface
- `ProcessResult.kt` - Process result types

**MCP Declarations** (`mcp-audio-tools/src/commonMain/kotlin/mcptool/`):
- Annotated with `@McpTool`, `@McpResource`, `@McpPrompt` from `mcp-tool-annotations`
- `TranscodeWavToMp3.kt` - WAV to MP3 transcoding using ffmpeg (`@McpTool`)
- `SubtitleToLrc.kt` - Subtitle format conversion (`@McpTool`, requires external `subtitle_to_lrc` binary)
- `ExecuteCommand.kt` - Generic shell command execution (`@McpTool`)
- `AudioResources.kt` - MCP resource definitions (`@McpResource`)
- `AudioPrompts.kt` - MCP prompt definitions (`@McpPrompt`)

**KSP Code Generation**:
- Annotations defined in `mcp-tool-annotations/src/commonMain/kotlin/`
- Processor in `mcp-tool-ksp/src/main/kotlin/`
- Generated code: `io.github.qingshu.mcpaudiotools.mcptool.GeneratedMcpTools`
- Build wiring: KSP runs before compilation (see `mcp-audio-tools/build.gradle.kts` lines 69-78)

**MCP Server Process** (`mcp-audio-tools/src/commonMain/kotlin/Process.kt`):
- Separate `Process` interface for stdio transport (input: Source, output: Sink)
- Used to connect MCP server to stdin/stdout for stdio transport
- Different from the subprocess `Process` in the `process` module

## Development Commands

### Build
```bash
./gradlew build              # Build all modules, run tests
./gradlew assemble           # Build artifacts without testing
./gradlew :mcp-audio-tools:jvmJar  # Build JVM JAR only
./gradlew :mcp-audio-tools:fatJar  # Build fat/uber JAR
```

### Native Binaries
```bash
./gradlew :mcp-audio-tools:linkReleaseExecutableLinuxX64   # Linux x64
./gradlew :mcp-audio-tools:linkReleaseExecutableLinuxArm64 # Linux ARM64
./gradlew :mcp-audio-tools:linkReleaseExecutableMingwX64   # Windows
```

### Testing
```bash
./gradlew test               # Run all tests (common + platform-specific)
./gradlew :mcp-audio-tools:jvmTest  # Run JVM tests only
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
./gradlew :mcp-audio-tools:kspCommonMainKotlinMetadata  # Run KSP manually
```
The KSP task is automatically wired to run before compilation tasks (see `mcp-audio-tools/build.gradle.kts`).

### Running the Server
```bash
# JVM (development):
java -jar mcp-audio-tools/build/libs/mcp-audio-tools-jvm-1.0.0.jar

# Or run directly:
./gradlew :mcp-audio-tools:jvmRun

# Native (after linking):
./mcp-audio-tools/build/bin/linuxX64/executable/mcp-audio-tools-1.0.0
```

## Code Conventions

### Process Handling
- Use coroutines for async I/O: `launch { process.stdoutLines().collect { ... } }`
- Always redirect/merge stderr when capturing output
- Use `awaitExit()` for clean shutdown
- Create output directories with `SystemFileSystem.createDirectories(Path(output))`

### MCP Declarations
- Annotate tool functions with `@McpTool` and parameters with `@ToolParam`
- Annotate resource functions with `@McpResource`
- Annotate prompt functions with `@McpPrompt` and parameters with `@PromptParam`
- Functions return `String` — KSP generates the wrapper that produces `CallToolResult`
- Use `[OK]`/`[Failed]` prefixes for tool output messages
- KSP auto-generates registration code in `GeneratedMcpTools`

### Platform-Specific Code
- Use `expect`/`actual` declarations in `mcp-audio-tools/src/commonMain/kotlin/Platform.kt`
- Platform implementations in: `jvmMain`, `linuxMain`, `mingwMain`, `nativeMain`
- For mcp-audio-tools platform code: `mcp-audio-tools/src/{jvm,linux,mingw,native}Main/kotlin/`
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

## Notes

- The project includes an Aliyun Maven mirror for dependency resolution (configured in `settings.gradle.kts`)
- CI environment uses official Gradle distribution; local uses mirrored distribution
- All code is formatted with Spotless using ktlint with custom rules
- Tests include platform-specific process handling tests; may require actual binaries (ffmpeg) on the target platform
- The `subtitle_to_lrc` tool expects an external binary in PATH or configured via `SUBTITLE_TO_LRC` env var
- The `process` module is published as a separate library (group: `io.github.qingshu-ui`, name: `process`)
- KSP-generated sources are in `build/generated/ksp/metadata/commonMain/kotlin/` and wired into the compilation

## File Locations (Quick Reference)

- Build config: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`
- Gradle wrapper: `gradle/wrapper/`
- Annotations: `mcp-tool-annotations/src/commonMain/kotlin/`
- KSP processor: `mcp-tool-ksp/src/main/kotlin/`
- Process library: `process/src/{jvm,linux,mingw}Main/kotlin/` (package: `io.github.qingshu.process`)
- MCP server: `mcp-audio-tools/src/commonMain/kotlin/`
- MCP declarations (tools/resources/prompts): `mcp-audio-tools/src/commonMain/kotlin/mcptool/`
- Generated code: `mcp-audio-tools/build/generated/ksp/metadata/commonMain/kotlin/`
- Tests: `**/src/**/test/kotlin/`
