# MCP Audio Tools

A Kotlin Multiplatform MCP (Model Context Protocol) server that provides audio processing, subtitles, and command execution capabilities.

## Features

MCP server running over stdio transport, providing three declaration types:

### Tools

| Tool | Description |
|------|-------------|
| `transcode_wav_to_mp3` | Convert WAV audio files to MP3 using ffmpeg |
| `subtitle_to_lrc` | Convert subtitle files (SRT/VTT) to LRC format |
| `execute_command` | Execute arbitrary shell commands |
| `understand_image` | Analyze images using a vision model (requires `VISION_API_KEY` env var) |

### Resources

| Resource | Description |
|----------|-------------|
| `audio://server/info` | Static server information |
| `audio://files/{path}/summary` | Template-based audio file summary |

### Prompts

| Prompt | Description |
|--------|-------------|
| `summarize_audio` | Create a prompt for summarizing an audio file |

## Development

### Project Structure

```
MyMCPTools/
├── mcp-tool-annotations/            # Multiplatform annotations
│   └── src/commonMain/kotlin/       #   @McpTool, @ToolParam, @McpResource, @McpPrompt, @PromptParam
├── mcp-tool-ksp/                    # KSP processor for code generation
│   └── src/
│       ├── main/kotlin/             #   Processor & validators
│       └── test/kotlin/             #   Processor tests
├── process/                         # Shared process handling (expect/actual)
│   └── src/
│       ├── commonMain/kotlin/       #   Common Process/ProcessBuilder API
│       ├── jvmMain/kotlin/          #   JVM implementation
│       ├── linuxMain/kotlin/        #   Linux native implementation
│       └── mingwMain/kotlin/        #   Windows native implementation
├── essential-mcp/                 # Main MCP server
│   └── src/
│       ├── commonMain/kotlin/       #   Shared code, tool/resource/prompt implementations
│       │   ├── mcptool/             #     Tool implementations
│       │   └── utils/               #     Utilities
│       ├── commonTest/kotlin/       #   Common tests
│       ├── jvmMain/kotlin/          #   JVM entry point
│       ├── linuxMain/kotlin/        #   Linux native entry
│       ├── mingwMain/kotlin/        #   Windows native entry
│       └── nativeMain/kotlin/       #   Shared native entry
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
./gradlew spotlessApply      # Format code (Kotlin, Gradle KTS)
./gradlew spotlessCheck      # Check formatting without changes

# JVM
./gradlew :essential-mcp:jvmJar    # Build JVM JAR
./gradlew :essential-mcp:fatJar    # Build fat/uber JAR
./gradlew :essential-mcp:jvmRun    # Run directly

# JVM (standalone)
java -jar essential-mcp/build/libs/essential-mcp-jvm-1.1.0.jar

# Native executables
./gradlew :essential-mcp:linkReleaseExecutableLinuxX64
./gradlew :essential-mcp:linkReleaseExecutableLinuxArm64
./gradlew :essential-mcp:linkReleaseExecutableMingwX64

# Native (after linking)
./essential-mcp/build/bin/linuxX64/executable/essential-mcp-1.1.0
```

### Testing

```bash
./gradlew test               # Run all tests (common + platform-specific)
./gradlew :essential-mcp:jvmTest  # Run JVM tests only
./gradlew :mcp-tool-ksp:test        # Run KSP processor tests
./gradlew test --tests "*PlatformTest"  # Run specific test class
```

### KSP Code Generation

```bash
./gradlew :essential-mcp:kspCommonMainKotlinMetadata  # Run KSP manually
```

The KSP task is automatically wired to run before compilation tasks.
Generated sources are output to `build/generated/ksp/metadata/commonMain/kotlin/`.

## Defining MCP Declarations

### Tools

Tools are defined using `@McpTool`. KSP auto-generates registration code. Functions return `String` — the generated wrapper converts to `CallToolResult`:

```kotlin
@McpTool(
    name = "my_tool",
    description = "Does something useful"
)
suspend fun myTool(
    @ToolParam(description = "Input path") input: String,
    @ToolParam(description = "Output path") output: String
): String {
    // Implementation
    return "[OK] $output"
}
```

### Resources

Resources are defined using `@McpResource`:

```kotlin
@McpResource(
    uri = "audio://server/info",
    name = "audio_server_info",
    description = "Information about the MCP audio tools server.",
    mimeType = "text/plain",
)
fun audioServerInfo(): String = "MCP audio tools server"
```

### Prompts

Prompts are defined using `@McpPrompt`. Parameters must be annotated with `@PromptParam`:

```kotlin
@McpPrompt(
    name = "summarize_audio",
    description = "Create a prompt for summarizing an audio file.",
)
fun summarizeAudioPrompt(
    @PromptParam(description = "Path to the audio file.", name = "audio_path")
    audioPath: String,
): String = "Summarize the audio file at: $audioPath"
```

### Context Injection

Unannotated parameters with SDK context types are automatically injected in generated code:

```kotlin
@McpTool(name = "my_tool", description = "Does something useful")
suspend fun myTool(
    @ToolParam(description = "Input path") input: String,
    conn: ClientConnection,       // injected: the client connection
    request: CallToolRequest,     // injected: the original request
    server: Server,               // injected: the MCP server instance
): String {
    // Implementation
}
```

Supported context types per declaration:

| Declaration | Injectable types |
|---|---|
| `@McpTool` | `CallToolRequest`, `ClientConnection`, `Server` |
| `@McpResource` | `ReadResourceRequest`, `ClientConnection`, `Server` |
| `@McpPrompt` | `GetPromptRequest`, `ClientConnection`, `Server` |

### Available Annotations

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@McpTool` | Function | Define an MCP tool |
| `@ToolParam` | Parameter | Describe a tool parameter |
| `@McpResource` | Function | Define an MCP resource |
| `@McpPrompt` | Function | Define an MCP prompt |
| `@PromptParam` | Parameter | Describe a prompt parameter |

## Requirements

- **ffmpeg** must be installed and available in `PATH` for WAV-to-MP3 transcoding
- **subtitle_to_lrc** binary must be installed in `PATH` (or configured via `SUBTITLE_TO_LRC` env var) for subtitle conversion
- **Vision API** requires `VISION_API_KEY` env var (OpenAI-compatible). Optional: `VISION_API_URL` (default: `https://api.openai.com/v1`), `VISION_MODEL` (default: `gpt-4o`)

## Key Technologies

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.3.21 | Multiplatform support |
| Kotlin Coroutines | 1.10.2 | Async I/O for process handling |
| Kotlinx Serialization | 1.9.0 | JSON handling for MCP protocol |
| Kotlinx IO | 0.9.0 | IO abstraction |
| MCP SDK | 0.12.0 | Model Context Protocol implementation |
| KSP | 2.3.7 | Annotation processing |
| KotlinPoet | 2.2.0 | Code generation |
| Ktor | 3.4.1 | HTTP client for vision API |

## License

GNU Affero General Public License v3 (AGPLv3)
