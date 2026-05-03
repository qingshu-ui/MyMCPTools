# MCP Audio Tools

A Kotlin Multiplatform MCP (Model Context Protocol) server that provides audio processing and command execution capabilities.

## Features

| Tool | Description |
|------|-------------|
| `transcode_wav_to_mp3` | Convert WAV audio files to MP3 using ffmpeg |
| `subtitle_to_lrc` | Convert subtitle files (SRT/VTT) to LRC format |
| `execute_command` | Execute arbitrary shell commands |

## Development

### Project Structure

```
MyMCPTools/
├── mcp-tool-annotations/            # Multiplatform annotations (@McpTool, @ToolParam)
│   └── src/commonMain/kotlin/
├── mcp-tool-ksp/                    # KSP processor for code generation
│   └── src/main/kotlin/
├── process/                         # Shared process handling (expect/actual)
│   └── src/
│       ├── jvmMain/kotlin/          # JVM implementation
│       ├── linuxMain/kotlin/        # Linux native implementation
│       └── mingwMain/kotlin/        # Windows native implementation
├── mcp-audio-tools/                 # Main MCP server
│   └── src/
│       ├── commonMain/kotlin/       # Shared code & tool implementations
│       ├── jvmMain/                 # JVM entry point
│       ├── linuxMain/               # Linux native entry
│       └── mingwMain/               # Windows native entry
└── gradle/                          # Gradle wrapper
```

### Platform Targets

| Platform | Target | Output |
|----------|--------|--------|
| JVM | `jvm` | JAR / Fat JAR |
| Linux x64 | `linuxX64` | Native executable |
| Linux ARM64 | `linuxArm64` | Native executable |
| Windows | `mingwX64` | Native executable |

### Build Commands

```bash
# General
./gradlew build              # Build all modules, run tests
./gradlew assemble           # Build without testing
./gradlew spotlessApply      # Format code
./gradlew test               # Run tests

# JVM
./gradlew :mcp-audio-tools:jvmJar    # Build JVM JAR
./gradlew :mcp-audio-tools:fatJar    # Build fat/uber JAR

# Native executables
./gradlew :mcp-audio-tools:linkReleaseExecutableLinuxX64
./gradlew :mcp-audio-tools:linkReleaseExecutableLinuxArm64
./gradlew :mcp-audio-tools:linkReleaseExecutableMingwX64
```

### Defining MCP Tools

Tools are defined using annotations. KSP auto-generates registration code:

```kotlin
@McpTool(
    name = "my_tool",
    description = "Does something useful"
)
fun myTool(
    @ToolParam(description = "Input path") input: String,
    @ToolParam(description = "Output path") output: String
): CallToolResult {
    // Implementation
}
```

### Key Technologies

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.3.10 | Multiplatform support |
| Kotlin Coroutines | 1.10.2 | Async I/O for process handling |
| Kotlinx Serialization | 1.9.0 | JSON handling for MCP protocol |
| Kotlinx IO | 0.9.0 | IO abstraction |
| MCP SDK | 0.9.0 | Model Context Protocol implementation |
| Clikt | 5.1.0 | CLI framework |
| KSP | 2.3.7 | Annotation processing |
| KotlinPoet | 2.2.0 | Code generation |

## License

GNU Affero General Public License v3 (AGPLv3)