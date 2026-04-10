# MCP Audio Tools

A Kotlin Multiplatform MCP (Model Context Protocol) server that provides audio processing and command execution capabilities. Designed to work with Claude Desktop and other MCP clients.

## Features

| Tool | Description |
|------|-------------|
| `transcode_wav_to_mp3` | Convert WAV audio files to MP3 using ffmpeg |
| `subtitle_to_lrc` | Convert subtitle files (SRT/VTT) to LRC format |
| `execute_command` | Execute arbitrary shell commands |

## Requirements

- **ffmpeg**: Required for `transcode_wav_to_mp3`
- **subtitle_to_lrc**: External binary for `subtitle_to_lrc` (see [subtitle_to_lrc](https://github.com/qingshu1/subtitle_to_lrc))

## Installation

### Pre-built Binaries

Download the latest release for your platform from the [releases page](https://github.com/qingshu1/MyMCPTools/releases).

### Build from Source

Prerequisites:
- Kotlin 2.3.10
- Gradle 8.x

```bash
# Clone the repository
git clone https://github.com/qingshu1/MyMCPTools.git
cd MyMCPTools

# Build the JVM version
./gradlew :mcp-audio-tools:jvmJar

# Or build native executables
./gradlew :mcp-audio-tools:linkReleaseExecutableLinuxX64   # Linux x64
./gradlew :mcp-audio-tools:linkReleaseExecutableLinuxArm64 # Linux ARM64
./gradlew :mcp-audio-tools:linkReleaseExecutableMingwX64   # Windows
```

## Usage

### Running the Server

The server communicates over stdio transport, making it compatible with Claude Desktop and other MCP clients.

```bash
# JVM
java -jar mcp-audio-tools/build/libs/mcp-audio-tools-jvm-1.0.0.jar

# Native (Linux/macOS)
./mcp-audio-tools/build/bin/linuxX64/releaseExecutable/mcp-audio-tools-1.0.0
```

### Configuration

**Environment Variables:**

| Variable | Description | Default |
|----------|-------------|---------|
| `SUBTITLE_TO_LRC` | Path to subtitle_to_lrc binary | `subtitle_to_lrc` |

### Claude Desktop Integration

Add to your Claude Desktop config (`claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "audio-tools": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-audio-tools-jvm-1.0.0.jar"]
    }
  }
}
```

Or for native:

```json
{
  "mcpServers": {
    "audio-tools": {
      "command": "/path/to/mcp-audio-tools-1.0.0"
    }
  }
}
```

## Development

### Project Structure

```
MyMCPTools/
├── process/                          # Shared process handling (expect/actual)
│   └── src/
│       ├── jvmMain/kotlin/          # JVM implementation
│       ├── linuxMain/kotlin/        # Linux native implementation
│       └── mingwMain/kotlin/        # Windows native implementation
├── mcp-audio-tools/                 # Main MCP server
│   └── src/
│       ├── commonMain/kotlin/       # Shared code
│       ├── jvmMain/                 # JVM entry point
│       ├── linuxMain/               # Linux native entry
│       └── nativeMain/              # Kotlin/Native entry
└── gradle/                          # Gradle wrapper
```

### Build Commands

```bash
./gradlew build              # Build all modules, run tests
./gradlew assemble           # Build without testing
./gradlew spotlessApply      # Format code
./gradlew test               # Run tests
```

### Key Technologies

- **Kotlin 2.3.10**: Multiplatform support
- **Kotlin Coroutines**: Async I/O for process handling
- **Kotlinx Serialization**: JSON handling for MCP protocol
- **MCP SDK 0.9.0**: Model Context Protocol implementation
- **Clikt 5.1.0**: CLI framework

## License

GNU Affero General Public License v3 (AGPLv3)