# KSP Annotation-Oriented MCP Tools Design

Date: 2026-04-30

## Goal

The MCP SDK registration APIs currently require too much boilerplate for each tool: manual schema construction, argument extraction, validation, and `CallToolResult` wrapping. This design introduces KSP-based code generation so tools can be authored as annotated Kotlin functions in `commonMain`, while generated adapters handle the MCP SDK plumbing.

## Scope

This first version targets common-first Kotlin Multiplatform tool definitions. It focuses on the existing `mcp-audio-tools` server and its current top-level tools. The design intentionally avoids a broad framework in v1: no grouped registries, no nested object schemas, no dependency-injected class tools, and no custom runtime abstraction layer.

## Module Structure

Add two new modules beside the existing `process` and `mcp-audio-tools` modules:

- `mcp-tool-annotations`
  - Multiplatform-friendly module.
  - Contains lightweight annotations such as `@McpTool` and `@ToolParam`.
  - Does not depend on the MCP SDK.
  - Is usable from `commonMain`.

- `mcp-tool-ksp`
  - JVM-only KSP processor module.
  - Scans annotated tool functions.
  - Validates signatures and metadata.
  - Generates MCP SDK registration code.

The existing `mcp-audio-tools` module will depend on `mcp-tool-annotations`, apply KSP, and call generated registration code from its handwritten `Server.mcpToolRegistry()` function.

## Authoring Model

The primary authoring model is an annotated top-level function:

```kotlin
@McpTool(
    name = "transcode_wav_to_mp3",
    description = "Uses ffmpeg to transcode a single .wav file to .mp3.",
)
suspend fun transcodeWavToMp3(
    @ToolParam("Absolute path to the source .wav file.") inputPath: String,
    @ToolParam("Absolute path for the output .mp3 file.") outputPath: String,
): String {
    return "[OK] $outputPath"
}
```

V1 supports top-level functions only. This avoids early design questions about constructors, dependency injection, instance lifetime, and object discovery. Class/object-based tools can be added later if a real need appears.

Every exposed function parameter must have `@ToolParam`. This keeps generated schemas documented and prevents accidental exposure of undocumented parameters.

## Generated Registration

The processor generates a single aggregate extension function, for example:

```kotlin
package io.github.qingshu.mcpaudiotools.generated

fun Server.registerGeneratedMcpTools() {
    registerTranscodeWavToMp3()
    registerSubtitleToLrc()
    registerExecuteCommand()
}
```

The existing registry becomes:

```kotlin
fun Server.mcpToolRegistry() {
    registerGeneratedMcpTools()
}
```

All annotated tools discovered in configured source sets are included automatically in the aggregate registry. Named groups and Gradle include lists are out of scope for v1.

Each generated per-tool registration function calls MCP SDK `Server.addTool(...)` directly. The generated adapter is responsible for:

1. Reading the tool name and description from `@McpTool`.
2. Building a `ToolSchema` from annotated parameters.
3. Computing required properties.
4. Extracting and converting JSON arguments.
5. Invoking the annotated function.
6. Converting the function result to `CallToolResult`.
7. Catching thrown exceptions and returning MCP error results.

## Parameter Schema Rules

Initial supported parameter types are:

- `String`
- `Int`
- `Long`
- `Double`
- `Boolean`
- Nullable/default variants of those types

Unsupported parameter types fail during KSP processing with clear diagnostics.

Required/optional behavior uses both Kotlin semantics and annotation metadata:

- Non-null parameter with no default is required.
- Nullable parameter or parameter with default value is optional.
- `@ToolParam(required = ...)` may override inferred requiredness only when it is consistent with Kotlin semantics.
- Contradictions are compile errors.

Example contradiction:

```kotlin
@ToolParam("Optional-looking value", required = true)
value: String? = null
```

This should fail because the schema says the field is required while the Kotlin signature says it can be absent.

## Return Values and Error Handling

Tool functions do not need to manually construct MCP SDK results for normal cases.

Supported v1 return behavior:

| Function return type | Generated behavior |
| --- | --- |
| `String` | `TextContent(value)`, `isError = false` |
| `Int`, `Long`, `Double`, `Boolean` | Converted to text content, `isError = false` |
| `Unit` | Success result with a short default message such as `[OK]` |
| `CallToolResult` | Passed through directly as an escape hatch |

Failures are reported by throwing exceptions with meaningful messages:

```kotlin
if (exitCode != 0) {
    error("ffmpeg failed (exit $exitCode):\n$stderr")
}
```

Generated adapters catch exceptions and return:

```kotlin
CallToolResult(
    content = listOf(TextContent(exception.message ?: "Tool failed")),
    isError = true,
)
```

This keeps ordinary tools concise while preserving a direct `CallToolResult` escape hatch for advanced cases.

## Migration of Existing Tools

Existing tools should move from manual `Server.addTool(...)` wrappers to annotated implementation functions. Most process-running logic remains intact; schema, argument extraction, and result wrapping move into generated code.

For example, `transcodeWavToMp3` changes from a `Server` extension that registers a tool into a suspend function that receives typed parameters, runs ffmpeg, throws on failure, and returns a success string.

`McpTools.kt` no longer lists individual handwritten registration functions. It imports the generated aggregate registry and delegates to it.

## Validation Rules

The KSP processor should produce compile-time errors for invalid definitions:

- `@McpTool` is allowed only on top-level functions in v1.
- Tool functions may be suspend or non-suspend.
- Every exposed parameter must have `@ToolParam`.
- Tool names must be non-blank.
- Tool descriptions must be non-blank.
- Parameter descriptions must be non-blank.
- Parameter types must be in the supported MVP set.
- Required/optional metadata must not contradict Kotlin nullability/defaults.
- Duplicate MCP tool names are compile errors.
- Unsupported return types are compile errors, except direct `CallToolResult`.

Diagnostics should name the offending function or parameter and explain how to fix the issue.

## Testing Strategy

Testing should focus on both code generation correctness and preservation of existing server behavior:

- Unit-test processor validation with compile-testing fixtures where practical.
- Snapshot or golden-file-test generated Kotlin for representative tools.
- Add integration-style tests in `mcp-audio-tools` verifying that:
  - Generated registry compiles.
  - Existing three tools can be registered through `registerGeneratedMcpTools()`.
  - Generated schemas contain expected properties and required fields.
- Avoid requiring external binaries such as `ffmpeg` in ordinary code-generation tests.

## Gradle and Multiplatform Considerations

The processor module is JVM-only because KSP processors run on the JVM. The annotations module is multiplatform so annotated tool definitions can live in `commonMain`.

The application module must wire generated sources into the relevant Kotlin Multiplatform compilation. The first implementation should prefer the smallest Gradle configuration that generates code for the common tool definitions while preserving current targets: JVM, Linux x64, Linux ARM64, and Windows mingwX64.

## Non-Goals for V1

- No named tool groups.
- No explicit Gradle include/exclude list for tools.
- No class/object tool lifecycle support.
- No dependency injection support.
- No nested serializable object parameters.
- No custom JSON schema escape hatch.
- No MCP SDK abstraction layer.
- No custom project-owned result wrapper.

These can be revisited after the annotation model proves useful for the existing tools.
