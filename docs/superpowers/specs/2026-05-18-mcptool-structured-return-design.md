# MCP Tool Structured Return Design

## Goal

Allow `@McpTool` functions to return custom Kotlin types as MCP `structuredContent` when the return type is annotated with `@Serializable`.

## Current Behavior

`ToolValidator.resolveReturnType` accepts only `Unit`, `String`, primitive numeric/boolean types, and `CallToolResult`. A custom return type such as `ToolResult` is rejected during KSP validation with an unsupported return type error.

## Selected Approach

Add a new `ToolReturnType.SerializableStructuredType` return kind. Existing return types keep their current behavior. If a tool return type is not one of the existing supported types, validation accepts it only when the return type declaration is annotated with `kotlinx.serialization.Serializable`.

If the custom return type is not annotated with `@Serializable`, validation emits a clear error that lists the existing supported return types and explains that custom return types must be `@Serializable` to be emitted as `structuredContent`.

## Code Generation

For `SerializableStructuredType`, generated tool code will:

1. Call the original tool function.
2. Serialize the returned value with `kotlinx.serialization.json.Json.encodeToJsonElement(result)`.
3. Return `CallToolResult` with the serialized JSON assigned to `structuredContent`.

The generator will preserve existing behavior for `Unit`, `String`, primitive, and `CallToolResult` returns. If the MCP SDK constructor requires `content`, the generated structured result will use an empty content list.

## Components

- `ToolModels.kt`: add the structured return type model.
- `ToolValidator.kt`: detect `@Serializable` on otherwise unsupported custom return declarations and produce clearer errors for non-serializable custom returns.
- `ToolCodeGenerator.kt`: generate kotlinx serialization imports and `CallToolResult(structuredContent = ...)` handling for structured returns.
- KSP tests: verify generated code for serializable structured returns and validation failure for non-serializable custom returns.

## Data Flow

A user writes a tool returning a serializable custom type:

```kotlin
@Serializable
data class ToolResult(val status: String, val stdout: String, val stderr: String)

@McpTool(name = "test", description = "A test tool")
fun test(): ToolResult = ToolResult("ok", "", "")
```

KSP validates `ToolResult` as a serializable structured return. Generated registration code serializes the returned instance into a JSON element and places it in `CallToolResult.structuredContent`.

## Error Handling

Validation remains compile-time. Non-serializable custom return types are rejected by KSP before generated code is compiled. Runtime exception handling inside generated tool wrappers remains unchanged.

## Testing

Add or update processor tests to cover:

- A `@Serializable` custom return type generates structured content result handling.
- A non-serializable custom return type produces a validation error mentioning the `@Serializable` requirement.
- Existing return type rendering tests continue to pass unchanged.

The existing `essential-mcp/src/commonMain/kotlin/mcptool/McpDemo.kt` example should compile after the feature is implemented.
