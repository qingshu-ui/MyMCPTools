# Understand Image MCP Tool Design

## Overview

Add an `understand_image` MCP tool to the essential-mcp module that allows AI to analyze images via an OpenAI-compatible vision API. The tool accepts images as URLs, base64 data URIs, or local file paths, sends them to a vision model, and returns the textual result.

## Tool: `understand_image`

**Parameters:**
- `image` (String, required) — The image to analyze. Can be:
  - URL starting with `http`
  - Base64 data URI starting with `data:`
  - Local file path (encoded to base64 via `runProcess("base64", path)` and sent as data URI)
- `prompt` (String, required) — What to ask about the image

**Auto-detection logic:**
- Input starts with `http` → pass as URL to the API
- Input starts with `data:` → pass as data URI to the API
- Otherwise → treat as local file path, encode via `base64` command, wrap in `data:image/<ext>;base64,...` data URI where `<ext>` is inferred from the file extension (png, jpg, jpeg, gif, webp, etc., defaulting to png)

**Annotation:** Uses `@McpTool` — KSP generates `registerUnderstandImageTool()` as with other tools.

## API & Configuration

OpenAI-compatible `POST /v1/chat/completions` endpoint. Request body includes the model, a user message with `image_url` content part, and `max_tokens: 4096`. Auth via `Authorization: Bearer <key>` header.

Environment variables:
- `VISION_API_KEY` (required) — API key for the vision model service
- `VISION_API_URL` (default: `https://api.openai.com/v1`) — Base URL for the API
- `VISION_MODEL` (default: `gpt-4o`) — Model name to use

## HTTP Client

Use Ktor HTTP client instead of the `httpPostJson` expect/actual pattern:
- Common: `ktor-client-core`
- JVM engine: `ktor-client-okhttp`
- Native engine: `ktor-client-curl`

Remove the `httpPostJson` expect/actual declarations from Platform.kt, Platform.jvm.kt, and Platform.native.kt.

## Registration

In `Main.kt`, replace `registerGeneratedMcpTools()` with individual registration calls:

```kotlin
registerExecuteCommandTool()
registerSubtitleToLrcTool()
registerTranscodeWavToMp3Tool()
if (getEnv("VISION_API_KEY") != null) {
    registerUnderstandImageTool()
}
```

The `understand_image` tool is only registered when `VISION_API_KEY` is set. Other tools always register.

## Error Handling

- Missing `VISION_API_KEY` → tool not registered (never appears to AI)
- Invalid image input → `[Failed]` with guidance on supported formats
- `base64` command failure → `[Failed]` with the error
- HTTP error → `[Failed]` with status code and response body
- Parse error → `[Failed]` with raw response excerpt (first 500 chars)

## Build Changes

- Add Ktor dependencies to `libs.versions.toml` and `essential-mcp/build.gradle.kts`
- Remove `httpPostJson` from all Platform files
- Rewrite existing `UnderstandImage.kt` with this design
