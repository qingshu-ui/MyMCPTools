# Context Injection for MCP Declarations

## Problem

Currently, `@McpTool`/`@McpResource`/`@McpPrompt` functions cannot access MCP SDK context objects like `CallToolRequest`, `ClientConnection`, or `Server`. Every parameter must be annotated with `@ToolParam`/`@PromptParam`, which maps to the JSON schema — there's no way to receive framework-provided context.

The MCP SDK's `addTool` callback is `suspend ClientConnection.(CallToolRequest) -> CallToolResult`, so `ClientConnection` and `CallToolRequest` are already available inside the generated lambda. The generated code just doesn't pass them through to the user's function.

## Design

### Unannotated parameters as context injections

When a function parameter has **no annotation** and its type matches a known SDK context type, the KSP processor treats it as a context injection parameter instead of requiring `@ToolParam`/`@PromptParam`.

```
if (has @ToolParam/@PromptParam) → schema parameter (existing behavior)
else if (type matches known context type) → context parameter, inject at call site
else → error: unrecognized parameter type
```

### Supported context types

| Declaration | Injectable types |
|---|---|
| `@McpTool` | `CallToolRequest`, `ClientConnection`, `Server` |
| `@McpResource` | `ReadResourceRequest`, `ClientConnection`, `Server` |
| `@McpPrompt` | `GetPromptRequest`, `ClientConnection`, `Server` |

### Data model changes (`ToolModels.kt`)

Add a `ContextParameter` type and include it in `ToolFunction`, `ResourceFunction`, and `PromptFunction`:

```kotlin
data class ContextParameter(
    val name: String,
    val type: ContextParameterType,
)

enum class ContextParameterType {
    CallToolRequest,
    ReadResourceRequest,
    GetPromptRequest,
    ClientConnection,
    Server,
}

// ToolFunction, ResourceFunction, PromptFunction each add:
val contextParameters: List<ContextParameter>
```

### Parameter resolution changes

**ToolValidator.kt** — `toToolFunctionOrNull`:

Currently, every parameter must have `@ToolParam` or the processor errors. Change to:

1. For each parameter: if annotated with `@ToolParam` → existing schema handling
2. If not annotated and type matches a `ContextParameterType` → add to `contextParameters`
3. If not annotated and type doesn't match → error

Validation rules:
- Each context type may appear at most once per function
- Context parameters are not nullable (they're always provided by the framework)
- Context parameters must not have default values

**ResourcePromptValidator.kt** — similar changes for `toResourceFunctionOrNull` and `toPromptFunctionOrNull`:

For resources: unannotated parameters with context types are added to `contextParameters`. URI template parameters remain identified by name matching (existing behavior).

For prompts: unannotated parameters with context types are added to `contextParameters`. `@PromptParam` parameters remain schema parameters.

### Code generation changes (`ToolCodeGenerator.kt`)

**addTool callback** — currently generates:

```kotlin
addTool(name = ..., description = ..., inputSchema = ...) { request ->
    try {
        val arguments = request.params.arguments
        // extract schema params...
        val result = pkg.func(arg1 = arg1)
        // handle result...
    } catch ...
}
```

With context injection, the function call becomes:

```kotlin
val result = pkg.func(
    arg1 = arg1,
    conn = this,                        // ClientConnection
    request = request,                  // CallToolRequest
    server = this@registerMyTool,       // Server
)
```

Where the values come from:
- `CallToolRequest` / `ReadResourceRequest` / `GetPromptRequest` → the `request` lambda parameter
- `ClientConnection` → `this` inside the lambda (the receiver)
- `Server` → `this@registerXXXXTool` (the registration function's receiver)

**Invocation helpers** — when a tool has default-value parameters, the generated `invokeXXXTool` helper must also accept and forward context parameters.

**addResource callback** — similar: pass context parameters when calling the user function.

**addPrompt callback** — similar: pass context parameters when calling the user function.

### Files to modify

| File | Change |
|------|--------|
| `ToolModels.kt` | Add `ContextParameter`, `ContextParameterType`; add `contextParameters` field to `ToolFunction`, `ResourceFunction`, `PromptFunction` |
| `ToolValidator.kt` | Split parameter resolution into schema params + context params; validate context param constraints |
| `ResourcePromptValidator.kt` | Same split for resource and prompt functions |
| `ToolCodeGenerator.kt` | Generate context injection at call sites in `buildAddToolBlock`, `buildAddStaticResourceBlock`, `buildAddTemplateResourceBlock`, `buildAddPromptBlock`; update invocation helpers to forward context params |
| `McpToolProcessor.kt` | No logic changes, but context parameters flow through existing data structures |
| Tests | Add test cases for context parameter validation and code generation |
