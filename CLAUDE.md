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

### Multiplatform Structure
The project uses Kotlin Multiplatform with two modules:
- **process**: Shared process abstraction library (expect/actual pattern for Process/ProcessBuilder)
- **mcp-audio-tools**: Main MCP server application (depends on process)

### Platform Targets
- **JVM**: For desktop/CLI usage (main development target)
- **Linux** (x64, arm64): Native executables via Kotlin/Native
- **Windows** (mingwX64): Native executables via Kotlin/Native

### Key Components

**MCP Server** (mcp-audio-tools/src/commonMain/kotlin/):
- `Main.kt` - Entry point using Clikt CLI framework
- `Server.kt` - MCP server factory with stdio transport
- `McpTools.kt` - Tool registry that registers all MCP tools
- `Platform.kt` - Platform-specific expect declarations

**MCP Tools** (mcp-audio-tools/src/commonMain/kotlin/mcptool/):
- `TranscodeWavToMp3.kt` - WAV to MP3 transcoding using ffmpeg
- `SubtitleToLrc.kt` - Subtitle format conversion (requires external `subtitle_to_lrc` binary)
- `ExecuteCommand.kt` - Generic shell command execution

**Process Abstraction** (process/src/):
- `Process.kt` & `ProcessBuilder.kt` - Expect declarations for subprocess handling
- Package: `io.github.qingshu.process`
- Platform implementations: JVM, Linux (uses FDs), Windows (uses handles)
- Utility functions: `awaitExit()`, `stdoutLines()`, `stderrLines()`, `exec()`

**MCP Server Process** (mcp-audio-tools/src/commonMain/kotlin/):
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
./gradlew test --tests "*PlatformTest"  # Run specific test class
```

### Linting & Formatting
```bash
./gradlew spotlessApply      # Format all code (Kotlin, Gradle KTS)
./gradlew spotlessCheck      # Check formatting without changes
```

### Development Workflow
```bash
./gradlew clean build        # Clean build
./gradlew build --info       # Build with detailed logs
./gradlew build --scan       # Build with build scan (uploaded to Gradle)
```

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

### MCP Tools
- Register tools in `McpTools.mcpToolRegistry()`
- Use `requireArgs()` utility for argument validation
- Return `CallToolResult` with appropriate `isError` flag
- Use `TextContent` for output messages with `[OK]`/`[Failed]` prefixes

### Platform-Specific Code
- Use `expect`/`actual` declarations in `mcp-audio-tools/src/commonMain/kotlin/Platform.kt`
- Platform implementations in: `jvmMain`, `linuxMain`, `mingwMain`, `nativeMain`
- For mcp-audio-tools platform code: `mcp-audio-tools/src/{jvm,linux,mingw,native}Main/kotlin/`
- For process library: `process/src/{jvm,linux,mingw}Main/kotlin/`

## Dependencies

Core libraries (from gradle/libs.versions.toml):
- Kotlin 2.3.10
- kotlinx.coroutines 1.10.2
- kotlinx.serialization 1.9.0
- kotlinx.io 0.9.0
- Model Context Protocol SDK 0.9.0
- Clikt (CLI) 5.1.0

## Notes

- The project includes an Aliyun Maven mirror for dependency resolution (configured in settings.gradle.kts)
- CI environment uses official Gradle distribution; local uses mirrored distribution
- All code is formatted with Spotless using ktlint with custom rules
- Tests include platform-specific process handling tests; may require actual binaries (ffmpeg) on the target platform
- The `subtitle_to_lrc` tool expects an external binary in PATH or configured via `SUBTITLE_TO_LRC` env var
- The `process` module is published as a separate library (group: `io.github.qingshu-ui`, name: `process`)

## File Locations (Quick Reference)

- Build config: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`
- Gradle wrapper: `gradle/wrapper/`
- Process library: `process/src/{jvm,linux,mingw}Main/kotlin/` (package: `io.github.qingshu.process`)
- MCP server: `mcp-audio-tools/src/commonMain/kotlin/`
- Tests: `**/src/**/test/kotlin/`
