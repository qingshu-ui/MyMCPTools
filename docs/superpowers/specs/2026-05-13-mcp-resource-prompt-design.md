# MCP Resource and Prompt Annotation Design

## Goal

Add first-class `@McpResource` and `@McpPrompt` support alongside the existing `@McpTool` annotation pipeline. The implementation should be MCP-native rather than a shallow copy of tool generation, while keeping the same Kotlin Multiplatform annotation and KSP-driven registration style.

## Architecture

The existing KSP processor will become a broader MCP declaration generator. It will continue to generate tool registration, and will add resource and prompt registration from annotated top-level functions.

The annotation module will add:

- `@McpResource` for MCP resources.
- `@McpPrompt` for MCP prompts.
- `@PromptParam` for prompt arguments.

The KSP module will add parallel models, validators, and code generation for:

- tools through `Server.addTool`.
- resources through `Server.addResource`, with generated URI-template matching for dynamic resources.
- prompts through `Server.addPrompt`.

Generated code will expose separate registration functions:

- `registerGeneratedMcpTools()`.
- `registerGeneratedMcpResources()`.
- `registerGeneratedMcpPrompts()`.
- `registerGeneratedMcpDeclarations()` as the aggregate registration entry point.

`essential-mcp/src/commonMain/kotlin/McpTools.kt` will call the aggregate registration function so application startup stays simple.

## Annotation API

Static resource example:

```kotlin
@McpResource(
    uri = "file:///config",
    name = "config",
    description = "Current configuration",
    mimeType = "application/json",
)
fun readConfig(): String
```

Dynamic resource example:

```kotlin
@McpResource(
    uriTemplate = "audio://tracks/{id}/metadata",
    name = "track_metadata",
    description = "Metadata for an audio track",
    mimeType = "application/json",
)
fun readTrackMetadata(id: String): String
```

Prompt example:

```kotlin
@McpPrompt(
    name = "summarize_audio",
    description = "Create a summary prompt for an audio file.",
)
fun summarizeAudioPrompt(
    @PromptParam(description = "Path to the audio file", name = "audio_path")
    audioPath: String,
): String
```

Resource functions will support `suspend` and these return shapes:

- `String`, converted to `ReadResourceResult` with one `TextResourceContents`.
- `TextResourceContents`.
- `BlobResourceContents`.
- `ReadResourceResult`.

Prompt functions will support `suspend` and these return shapes:

- `String`, converted to a single user `PromptMessage` with `TextContent`.
- `PromptMessage`.
- `List<PromptMessage>`.
- `GetPromptResult`.

Prompt arguments and URI-template variables will use the same supported primitive types as tool parameters: `String`, `Int`, `Long`, `Double`, and `Boolean`. Nullability, defaults, and explicit requiredness will follow the existing `@ToolParam` behavior where applicable.

## Dynamic Resource Behavior

The implementation will target MCP Kotlin SDK 0.12.0. Before coding dynamic resources, inspect the 0.12.0 server API and use its public resource-template registration and routing APIs if they exist.

Static resources will register directly through the SDK's static resource API.

Dynamic resources will use SDK-native resource-template support when available. URI templates such as `audio://tracks/{id}/metadata` will be exposed to MCP clients as resource templates, and incoming read requests will route through the generated handler for the matching template. Template placeholders become function parameters. Matched variable text is passed directly for `String` parameters. Numeric and Boolean parameters are parsed using the same invalid-argument behavior as tools.

If SDK 0.12.0 still lacks public APIs for resource-template registration and `resources/read` routing, the implementation will add a small project-local server extension layer rather than pretending dynamic resources are static resources. That layer will register `ResourceTemplate` metadata for list operations and route `resources/read` through generated URI-template matchers before invoking annotated functions.

Overlapping dynamic URI templates will be rejected when the processor can identify ambiguity reliably. Otherwise, templates will be ordered deterministically by generated declaration order and normalized generated names will be made unique the same way tool helper names are made unique.

## Validation

The processor will report KSP errors for invalid declarations:

- `@McpResource` must specify exactly one of `uri` or `uriTemplate`.
- resource and prompt names must not be blank.
- resource descriptions must not be blank.
- prompt descriptions must not be blank.
- static resource functions must not require URI-template variables.
- URI-template placeholders must map to function parameters.
- URI-template function parameters must be supported primitive types.
- prompt function parameters must be annotated with `@PromptParam`.
- duplicate public MCP names fail per kind: duplicate tool names, duplicate resource names or URIs, and duplicate prompt names.
- unsupported return types fail with messages that list the supported types for that declaration kind.

Top-level function restrictions will match `@McpTool` v1 behavior.

## Error Handling

Generated handlers will catch exceptions from annotated functions and convert them to MCP result errors where the SDK provides an appropriate result shape.

For resources, handler failures will return a `ReadResourceResult` that communicates the failure through supported MCP content or result metadata if a typed error result is not available in SDK 0.12.0. The implementation should prefer SDK-native error mechanisms if available after inspecting the server internals.

For prompts, handler failures will return a `GetPromptResult` with a text prompt message describing the failure if no typed prompt error result exists.

Invalid dynamic resource variables and prompt arguments will be handled before function invocation and reported consistently with existing tool argument errors.

## Generated Code Organization

The implementation should avoid growing the existing tool generator into an unbounded monolith. Shared pieces should be factored around:

- generated Kotlin name normalization and collision handling.
- parameter metadata and requiredness inference.
- primitive argument parsing.
- JSON schema generation for tool and prompt arguments.
- return conversion helpers per declaration kind.

Tool generation should remain behaviorally unchanged except for any shared helper extraction needed to support resources and prompts.

## Testing

Tests will cover:

- annotation validation for resources and prompts.
- static resource registration generation.
- URI-template extraction and invocation.
- invalid and missing dynamic resource variable handling.
- prompt argument schema generation.
- prompt argument parsing and defaults.
- return conversion for simple and SDK-native resource return types.
- return conversion for simple and SDK-native prompt return types.
- generated name collision handling.
- compile verification in `essential-mcp/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt` or an equivalent compile test.

Existing tool generation tests must continue to pass without behavior changes.

## Scope

This design includes static resources, dynamic URI-template resources, prompts with arguments, generated registration functions, validation, runtime conversion, and tests.

It does not include a package rename away from `io.github.qingshu.mcptool.annotations`, feature flags, or backwards-compatibility shims beyond preserving existing `@McpTool` behavior.
