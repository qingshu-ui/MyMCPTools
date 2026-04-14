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