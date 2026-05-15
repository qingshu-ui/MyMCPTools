# Context Injection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow `@McpTool`/`@McpResource`/`@McpPrompt` functions to receive SDK context objects (`CallToolRequest`, `ClientConnection`, `Server`, etc.) as unannotated parameters that are injected at the call site in generated code.

**Architecture:** The KSP processor currently requires every parameter to be annotated with `@ToolParam`/`@PromptParam`. We extend parameter resolution so unannotated parameters whose types match known SDK context types are treated as injection targets. The code generator then passes the appropriate values (`this` for `ClientConnection`, `request` for request types, `this@registerXxx` for `Server`) when calling the user function.

**Tech Stack:** Kotlin, KSP, KotlinPoet, kotlinx.serialization, MCP SDK 0.12.0

---

### Task 1: Add `ContextParameter` and `ContextParameterType` to `ToolModels.kt`

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolModels.kt`
- Test: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Write the failing test for `ContextParameterType.fromQualifiedName`**

Add to `ToolModelsTest.kt`:

```kotlin
@Test
fun `maps SDK context types to ContextParameterType`() {
    assertEquals(ContextParameterType.CallToolRequest, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest"))
    assertEquals(ContextParameterType.ReadResourceRequest, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest"))
    assertEquals(ContextParameterType.GetPromptRequest, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest"))
    assertEquals(ContextParameterType.ClientConnection, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.server.ClientConnection"))
    assertEquals(ContextParameterType.Server, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.server.Server"))
}

@Test
fun `returns null for non-context qualified names`() {
    assertNull(ContextParameterType.fromQualifiedName("kotlin.String"))
    assertNull(ContextParameterType.fromQualifiedName("com.example.Custom"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :mcp-tool-ksp:test --tests "ToolModelsTest.maps SDK context types to ContextParameterType" -q`
Expected: FAIL — `ContextParameterType` unresolved

- [ ] **Step 3: Add `ContextParameter` and `ContextParameterType` to `ToolModels.kt`**

Add after the `ParameterType` class in `ToolModels.kt`:

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
    Server;

    companion object {
        private val QUALIFIED_NAMES: Map<String, ContextParameterType> = mapOf(
            "io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest" to CallToolRequest,
            "io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest" to ReadResourceRequest,
            "io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest" to GetPromptRequest,
            "io.modelcontextprotocol.kotlin.sdk.server.ClientConnection" to ClientConnection,
            "io.modelcontextprotocol.kotlin.sdk.server.Server" to Server,
        )

        fun fromQualifiedName(qualifiedName: String): ContextParameterType? = QUALIFIED_NAMES[qualifiedName]
    }
}
```

- [ ] **Step 4: Add `contextParameters` field to `ToolFunction`, `ResourceFunction`, `PromptFunction`**

In `ToolModels.kt`, update the three data classes:

```kotlin
data class ToolFunction(
    val packageName: String,
    val functionName: String,
    val toolName: String,
    val description: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val contextParameters: List<ContextParameter>,
    val returnType: ToolReturnType,
)

data class ResourceFunction(
    val packageName: String,
    val functionName: String,
    val resourceName: String,
    val description: String,
    val location: ResourceLocation,
    val mimeType: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val contextParameters: List<ContextParameter>,
    val returnType: ResourceReturnType,
)

data class PromptFunction(
    val packageName: String,
    val functionName: String,
    val promptName: String,
    val description: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val contextParameters: List<ContextParameter>,
    val returnType: PromptReturnType,
)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :mcp-tool-ksp:test --tests "ToolModelsTest" -q`
Expected: FAIL — existing tests reference `ToolFunction`/`ResourceFunction`/`PromptFunction` constructors without `contextParameters`. We fix those in Step 6.

- [ ] **Step 6: Fix all existing `ToolFunction`/`ResourceFunction`/`PromptFunction` constructor calls in tests**

In `ToolModelsTest.kt`, add `contextParameters = emptyList()` to every `ToolFunction(...)`, `ResourceFunction(...)`, and `PromptFunction(...)` constructor call.

In `ToolValidatorTest.kt`, the `toolParameter` helper does not construct these data classes, so no changes needed there.

- [ ] **Step 7: Run all KSP tests**

Run: `./gradlew :mcp-tool-ksp:test -q`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolModels.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: add ContextParameter and ContextParameterType to KSP models"
```

---

### Task 2: Update `ToolValidator.kt` to recognize context parameters

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolValidator.kt`

- [ ] **Step 1: Modify `toToolParameterOrNull` to return a sealed result**

Currently `toToolParameterOrNull` returns `ToolParameter?` and errors on unannotated params. Replace the logic to return a sealed type so we can distinguish schema params from context params from errors.

Add at the top of `ToolValidator.kt` (after imports):

```kotlin
internal sealed class ParameterResolution {
    data class Schema(val parameter: ToolParameter) : ParameterResolution()
    data class Context(val parameter: ContextParameter) : ParameterResolution()
}
```

- [ ] **Step 2: Replace `toToolParameterOrNull` with `resolveParameter`**

Replace the existing `toToolParameterOrNull` function with:

```kotlin
private fun KSValueParameter.resolveParameter(logger: KSPLogger): ParameterResolution? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpTool parameters must have stable names.", this)
        return null
    }

    val resolvedType = type.resolve()
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()

    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == TOOL_PARAM_ANNOTATION
    }

    if (annotation != null) {
        val description = annotation.argumentValue<String>("description").orEmpty()
        if (description.isBlank()) {
            logger.error("@ToolParam description for '$parameterName' must not be blank.", this)
            return null
        }

        val schemaName = resolveSchemaName(
            annotationName = annotation.argumentValue("name"),
            parameterName = parameterName,
        )

        val parameterType = ParameterType.fromQualifiedName(qualifiedType)
        if (parameterType == null) {
            logger.error(
                "Unsupported @ToolParam type '$qualifiedType' for '$parameterName'. Supported types: String, Int, Long, Double, Boolean.",
                this,
            )
            return null
        }

        val explicitRequired = annotation.requiredArgumentValue() ?: Required.UNSPECIFIED

        val required = try {
            inferRequiredness(
                nullable = resolvedType.isMarkedNullable,
                hasDefault = hasDefault,
                explicit = explicitRequired,
            )
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid requiredness for parameter '$parameterName': ${e.message}", this)
            return null
        }

        return ParameterResolution.Schema(
            ToolParameter(
                name = parameterName,
                schemaName = schemaName,
                description = description,
                type = parameterType,
                nullable = resolvedType.isMarkedNullable,
                hasDefault = hasDefault,
                required = required,
            ),
        )
    }

    val contextType = ContextParameterType.fromQualifiedName(qualifiedType)
    if (contextType != null) {
        if (resolvedType.isMarkedNullable) {
            logger.error("Context parameter '$parameterName' must not be nullable.", this)
            return null
        }
        if (hasDefault) {
            logger.error("Context parameter '$parameterName' must not have a default value.", this)
            return null
        }
        return ParameterResolution.Context(
            ContextParameter(
                name = parameterName,
                type = contextType,
            ),
        )
    }

    logger.error(
        "Parameter '$parameterName' must be annotated with @ToolParam or be a recognized context type (CallToolRequest, ClientConnection, Server).",
        this,
    )
    return null
}
```

- [ ] **Step 3: Update `toToolFunctionOrNull` to use `resolveParameter` and collect context parameters**

In `toToolFunctionOrNull`, replace:

```kotlin
val parameters = parameters.mapNotNull { parameter -> parameter.toToolParameterOrNull(logger) }
if (parameters.size != this.parameters.size) return null
```

with:

```kotlin
val schemaParameters = mutableListOf<ToolParameter>()
val contextParameters = mutableListOf<ContextParameter>()
val validContextTypes = setOf(
    ContextParameterType.CallToolRequest,
    ContextParameterType.ClientConnection,
    ContextParameterType.Server,
)
for (parameter in parameters) {
    when (val resolved = parameter.resolveParameter(logger)) {
        is ParameterResolution.Schema -> schemaParameters.add(resolved.parameter)
        is ParameterResolution.Context -> {
            if (resolved.parameter.type !in validContextTypes) {
                logger.error(
                    "Context type '${resolved.parameter.type}' is not supported for @McpTool. Supported: CallToolRequest, ClientConnection, Server.",
                    parameter,
                )
                return null
            }
            contextParameters.add(resolved.parameter)
        }
        null -> return null
    }
}
val duplicateContextTypes = contextParameters.groupBy { it.type }.filterValues { it.size > 1 }.keys
if (duplicateContextTypes.isNotEmpty()) {
    logger.error(
        "Duplicate context type(s) in @McpTool: ${duplicateContextTypes.joinToString { it.name }}",
        this,
    )
    return null
}
```

Then update the `ToolFunction` construction:

```kotlin
return ToolFunction(
    packageName = packageName.asString(),
    functionName = simpleName.asString(),
    toolName = toolName,
    description = description,
    isSuspend = modifiers.contains(Modifier.SUSPEND),
    parameters = schemaParameters,
    contextParameters = contextParameters,
    returnType = returnType,
)
```

And update the `validateUniqueSchemaNames` call to use `schemaParameters`:

```kotlin
if (!validateUniqueSchemaNames(schemaParameters, toolName, logger, this)) return null
```

- [ ] **Step 4: Run all KSP tests**

Run: `./gradlew :mcp-tool-ksp:test -q`
Expected: PASS (existing tests don't use context params, `contextParameters = emptyList()` is the default case)

- [ ] **Step 5: Commit**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolValidator.kt
git commit -m "feat: recognize context parameters in @McpTool validator"
```

---

### Task 3: Update `ResourcePromptValidator.kt` to recognize context parameters

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ResourcePromptValidator.kt`

- [ ] **Step 1: Add `resolveResourceParameter` to handle resource context params**

Add a helper function in `ResourcePromptValidator.kt`:

```kotlin
private val VALID_RESOURCE_CONTEXT_TYPES = setOf(
    ContextParameterType.ReadResourceRequest,
    ContextParameterType.ClientConnection,
    ContextParameterType.Server,
)

private fun KSValueParameter.resolveResourceParameter(logger: KSPLogger): ParameterResolution? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpResource parameters must have stable names.", this)
        return null
    }

    val resolvedType = type.resolve()
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()

    val contextType = ContextParameterType.fromQualifiedName(qualifiedType)
    if (contextType != null) {
        if (resolvedType.isMarkedNullable) {
            logger.error("Context parameter '$parameterName' must not be nullable.", this)
            return null
        }
        if (hasDefault) {
            logger.error("Context parameter '$parameterName' must not have a default value.", this)
            return null
        }
        if (contextType !in VALID_RESOURCE_CONTEXT_TYPES) {
            logger.error(
                "Context type '${contextType.name}' is not supported for @McpResource. Supported: ReadResourceRequest, ClientConnection, Server.",
                this,
            )
            return null
        }
        return ParameterResolution.Context(ContextParameter(name = parameterName, type = contextType))
    }

    val parameterType = ParameterType.fromQualifiedName(qualifiedType)
    if (parameterType == null) {
        logger.error(
            "Unsupported URI template parameter type '$qualifiedType' for '$parameterName'. Supported types: String, Int, Long, Double, Boolean.",
            this,
        )
        return null
    }
    return ParameterResolution.Schema(
        ToolParameter(
            name = parameterName,
            schemaName = parameterName,
            description = parameterName,
            type = parameterType,
            nullable = resolvedType.isMarkedNullable,
            hasDefault = hasDefault,
            required = inferRequiredness(
                resolvedType.isMarkedNullable,
                hasDefault,
                io.github.qingshu.mcptool.annotations.Required.UNSPECIFIED,
            ),
        ),
    )
}
```

- [ ] **Step 2: Add `resolvePromptParameter` to handle prompt context params**

```kotlin
private val VALID_PROMPT_CONTEXT_TYPES = setOf(
    ContextParameterType.GetPromptRequest,
    ContextParameterType.ClientConnection,
    ContextParameterType.Server,
)

private fun KSValueParameter.resolvePromptParameter(logger: KSPLogger): ParameterResolution? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpPrompt parameters must have stable names.", this)
        return null
    }

    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == PROMPT_PARAM_ANNOTATION
    }

    if (annotation != null) {
        val description = annotation.argumentValue<String>("description").orEmpty()
        if (description.isBlank()) {
            logger.error("@PromptParam description for '$parameterName' must not be blank.", this)
            return null
        }
        val schemaName = resolveSchemaName(annotation.argumentValue("name"), parameterName)
        val resolvedType = type.resolve()
        val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()
        val parameterType = ParameterType.fromQualifiedName(qualifiedType)
        if (parameterType == null) {
            logger.error(
                "Unsupported @PromptParam type '$qualifiedType' for '$parameterName'. Supported types: String, Int, Long, Double, Boolean.",
                this,
            )
            return null
        }
        val explicitRequired = annotation.requiredArgumentValue()
            ?: io.github.qingshu.mcptool.annotations.Required.UNSPECIFIED
        val required = try {
            inferRequiredness(resolvedType.isMarkedNullable, hasDefault, explicitRequired)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid requiredness for parameter '$parameterName': ${e.message}", this)
            return null
        }
        return ParameterResolution.Schema(
            ToolParameter(
                name = parameterName,
                schemaName = schemaName,
                description = description,
                type = parameterType,
                nullable = resolvedType.isMarkedNullable,
                hasDefault = hasDefault,
                required = required,
            ),
        )
    }

    val resolvedType = type.resolve()
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()
    val contextType = ContextParameterType.fromQualifiedName(qualifiedType)
    if (contextType != null) {
        if (resolvedType.isMarkedNullable) {
            logger.error("Context parameter '$parameterName' must not be nullable.", this)
            return null
        }
        if (hasDefault) {
            logger.error("Context parameter '$parameterName' must not have a default value.", this)
            return null
        }
        if (contextType !in VALID_PROMPT_CONTEXT_TYPES) {
            logger.error(
                "Context type '${contextType.name}' is not supported for @McpPrompt. Supported: GetPromptRequest, ClientConnection, Server.",
                this,
            )
            return null
        }
        return ParameterResolution.Context(ContextParameter(name = parameterName, type = contextType))
    }

    logger.error(
        "Parameter '$parameterName' must be annotated with @PromptParam or be a recognized context type (GetPromptRequest, ClientConnection, Server).",
        this,
    )
    return null
}
```

- [ ] **Step 3: Update `toResourceFunctionOrNull` to split parameters**

Replace:

```kotlin
val parameters = parameters.mapNotNull { parameter -> parameter.toUriTemplateParameterOrNull(logger) }
if (parameters.size != this.parameters.size) return null
```

with:

```kotlin
val schemaParameters = mutableListOf<ToolParameter>()
val contextParameters = mutableListOf<ContextParameter>()
for (parameter in parameters) {
    when (val resolved = parameter.resolveResourceParameter(logger)) {
        is ParameterResolution.Schema -> schemaParameters.add(resolved.parameter)
        is ParameterResolution.Context -> contextParameters.add(resolved.parameter)
        null -> return null
    }
}
val duplicateContextTypes = contextParameters.groupBy { it.type }.filterValues { it.size > 1 }.keys
if (duplicateContextTypes.isNotEmpty()) {
    logger.error(
        "Duplicate context type(s) in @McpResource: ${duplicateContextTypes.joinToString { it.name }}",
        this,
    )
    return null
}
```

Update the validation calls:

```kotlin
when (location) {
    is ResourceLocation.Static -> validateStaticResourceParameters(schemaParameters.map { it.name })
    is ResourceLocation.Template -> validateUriTemplateParameters(
        location.uriTemplate,
        schemaParameters.map { it.name }.toSet(),
    )
    null -> null
}
```

Update the `ResourceFunction` construction:

```kotlin
return ResourceFunction(
    packageName = packageName.asString(),
    functionName = simpleName.asString(),
    resourceName = name,
    description = description,
    location = location!!,
    mimeType = mimeType,
    isSuspend = modifiers.contains(Modifier.SUSPEND),
    parameters = schemaParameters,
    contextParameters = contextParameters,
    returnType = returnType,
)
```

- [ ] **Step 4: Update `toPromptFunctionOrNull` to split parameters**

Replace:

```kotlin
val parameters = parameters.mapNotNull { parameter -> parameter.toPromptParameterOrNull(logger) }
if (parameters.size != this.parameters.size) return null
if (!validateUniqueSchemaNames(parameters, name, logger, this)) return null
```

with:

```kotlin
val schemaParameters = mutableListOf<ToolParameter>()
val contextParameters = mutableListOf<ContextParameter>()
for (parameter in parameters) {
    when (val resolved = parameter.resolvePromptParameter(logger)) {
        is ParameterResolution.Schema -> schemaParameters.add(resolved.parameter)
        is ParameterResolution.Context -> contextParameters.add(resolved.parameter)
        null -> return null
    }
}
val duplicateContextTypes = contextParameters.groupBy { it.type }.filterValues { it.size > 1 }.keys
if (duplicateContextTypes.isNotEmpty()) {
    logger.error(
        "Duplicate context type(s) in @McpPrompt: ${duplicateContextTypes.joinToString { it.name }}",
        this,
    )
    return null
}
if (!validateUniqueSchemaNames(schemaParameters, name, logger, this)) return null
```

Update the `PromptFunction` construction:

```kotlin
return PromptFunction(
    packageName = packageName.asString(),
    functionName = simpleName.asString(),
    promptName = name,
    description = description,
    isSuspend = modifiers.contains(Modifier.SUSPEND),
    parameters = schemaParameters,
    contextParameters = contextParameters,
    returnType = returnType,
)
```

- [ ] **Step 5: Remove old helper functions that are now superseded**

Remove the old `toUriTemplateParameterOrNull` and `toPromptParameterOrNull` private functions since they are replaced by `resolveResourceParameter` and `resolvePromptParameter`.

- [ ] **Step 6: Run all KSP tests**

Run: `./gradlew :mcp-tool-ksp:test -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add mcp-tool-ksp/src/main/kotlin/ResourcePromptValidator.kt
git commit -m "feat: recognize context parameters in @McpResource and @McpPrompt validators"
```

---

### Task 4: Update `ToolCodeGenerator.kt` — tool invocation with context injection

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt`
- Test: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Add helper to build context argument code**

In the `ToolCodeGenerator.Companion` class, add:

```kotlin
private fun ContextParameterType.codeBlock(registrationFunctionName: String): CodeBlock = when (this) {
    ContextParameterType.CallToolRequest -> CodeBlock.of("request")
    ContextParameterType.ReadResourceRequest -> CodeBlock.of("request")
    ContextParameterType.GetPromptRequest -> CodeBlock.of("request")
    ContextParameterType.ClientConnection -> CodeBlock.of("this")
    ContextParameterType.Server -> CodeBlock.of("this@%N", registrationFunctionName)
}
```

- [ ] **Step 2: Update `buildInvocation` to include context parameters**

Change `buildInvocation` to accept `contextParameters` and `registrationFunctionName`:

```kotlin
private fun buildInvocation(
    tool: ToolFunction,
    includedDefaults: Map<String, Boolean>,
    nonNullAssertionMode: NonNullAssertionMode,
    registrationFunctionName: String,
): CodeBlock {
    val code = CodeBlock.builder()
    code.add("%L.%L(\n⇥", tool.packageName, tool.functionName)
    tool.parameters.forEach { parameter ->
        if (parameter.hasDefault && includedDefaults[parameter.name] == false) return@forEach
        code.add(
            "%N = %L,\n",
            parameter.name,
            buildInvocationArgument(parameter, nonNullAssertionMode),
        )
    }
    tool.contextParameters.forEach { contextParameter ->
        code.add(
            "%N = %L,\n",
            contextParameter.name,
            contextParameter.type.codeBlock(registrationFunctionName),
        )
    }
    code.add("⇤)")
    return code.build()
}
```

- [ ] **Step 3: Update `buildAddToolBlock` to pass registration function name through**

In `buildAddToolBlock`, update the `buildInvocation` call to pass `generatedNames.registrationFunctionName(tool)`:

Replace:
```kotlin
buildInvocation(
    tool = tool,
    includedDefaults = tool.parameters.associate { it.name to true },
    nonNullAssertionMode = NonNullAssertionMode.SmartCasted,
)
```

with:
```kotlin
buildInvocation(
    tool = tool,
    includedDefaults = tool.parameters.associate { it.name to true },
    nonNullAssertionMode = NonNullAssertionMode.SmartCasted,
    registrationFunctionName = generatedNames.registrationFunctionName(tool),
)
```

- [ ] **Step 4: Update `buildInvocationBranches` to forward registration function name**

Change signature:
```kotlin
private fun buildInvocationBranches(
    code: CodeBlock.Builder,
    tool: ToolFunction,
    defaultParameters: List<ToolParameter>,
    includedDefaults: Map<String, Boolean>,
    registrationFunctionName: String,
)
```

Update the recursive calls and the terminal `buildInvocation` call to pass `registrationFunctionName`.

- [ ] **Step 5: Update `buildInvocationHelper` to forward context parameters**

In `buildInvocationHelper`, update the `buildInvocationBranches` call to pass the registration function name:

```kotlin
buildInvocationBranches(
    code = code,
    tool = tool,
    defaultParameters = tool.parameters.filter(ToolParameter::hasDefault),
    includedDefaults = emptyMap(),
    registrationFunctionName = generatedNames.registrationFunctionName(tool),
)
```

Also add context parameter arguments to the helper function signature. Add after the existing parameter loop:

```kotlin
tool.contextParameters.forEach { contextParameter ->
    builder.addParameter(
        ParameterSpec.builder(contextParameter.name, contextParameter.type.kotlinClassName()).build(),
    )
}
```

And add the `kotlinClassName()` helper to `ContextParameterType`:

```kotlin
fun ContextParameterType.kotlinClassName(): ClassName = when (this) {
    ContextParameterType.CallToolRequest -> ClassName("io.modelcontextprotocol.kotlin.sdk.types", "CallToolRequest")
    ContextParameterType.ReadResourceRequest -> ClassName("io.modelcontextprotocol.kotlin.sdk.types", "ReadResourceRequest")
    ContextParameterType.GetPromptRequest -> ClassName("io.modelcontextprotocol.kotlin.sdk.types", "GetPromptRequest")
    ContextParameterType.ClientConnection -> ClassName("io.modelcontextprotocol.kotlin.sdk.server", "ClientConnection")
    ContextParameterType.Server -> ClassName("io.modelcontextprotocol.kotlin.sdk.server", "Server")
}
```

And forward context parameters in the `buildAddToolBlock` invocation helper call:

```kotlin
tool.contextParameters.forEach { contextParameter ->
    code.addStatement("%N = %L,", contextParameter.name, contextParameter.type.codeBlock(generatedNames.registrationFunctionName(tool)))
}
```

- [ ] **Step 6: Add test for tool with context parameters**

In `ToolModelsTest.kt`, add:

```kotlin
@Test
fun `generates tool registration with context injection`() {
    val generated = ToolCodeGenerator.render(
        tools = listOf(
            ToolFunction(
                packageName = "com.example.tools",
                functionName = "myTool",
                toolName = "my_tool",
                description = "A tool with context.",
                isSuspend = true,
                parameters = listOf(
                    ToolParameter(
                        name = "input",
                        schemaName = "input",
                        description = "Input value",
                        type = ParameterType.StringType,
                        nullable = false,
                        hasDefault = false,
                        required = true,
                    ),
                ),
                contextParameters = listOf(
                    ContextParameter(name = "conn", type = ContextParameterType.ClientConnection),
                    ContextParameter(name = "req", type = ContextParameterType.CallToolRequest),
                    ContextParameter(name = "srv", type = ContextParameterType.Server),
                ),
                returnType = ToolReturnType.TextType,
            ),
        ),
    )

    assertTrue(generated.contains("name = \"my_tool\""), generated)
    assertTrue(generated.contains("conn = this,"), generated)
    assertTrue(generated.contains("req = request,"), generated)
    assertTrue(generated.contains("srv = this@registerMyToolTool,"), generated)
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :mcp-tool-ksp:test -q`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: generate context injection for @McpTool functions"
```

---

### Task 5: Update `ToolCodeGenerator.kt` — resource and prompt context injection

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt`
- Test: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Update `buildAddStaticResourceBlock` to inject context parameters**

After `code.beginControlFlow("try")`, replace:

```kotlin
code.add("val result = %L.%L()\n", resource.packageName, resource.functionName)
```

with:

```kotlin
code.add("val result = %L.%L(", resource.packageName, resource.functionName)
if (resource.contextParameters.isEmpty()) {
    code.add(")\n")
} else {
    code.add("\n")
    code.indent()
    resource.contextParameters.forEach { contextParameter ->
        code.addStatement(
            "%N = %L,",
            contextParameter.name,
            contextParameter.type.codeBlock(generatedNames.registrationFunctionName(resource)),
        )
    }
    code.unindent()
    code.add(")\n")
}
```

Note: We need to pass `generatedNames` to `buildAddStaticResourceBlock`. Update the call chain: `buildAddResourceBlock` → `buildAddStaticResourceBlock` and `buildAddTemplateResourceBlock` to also accept `generatedNames`.

Update `buildAddResourceBlock`:

```kotlin
private fun buildAddResourceBlock(resource: ResourceFunction, generatedNames: GeneratedResourceNames): CodeBlock = when (val location = resource.location) {
    is ResourceLocation.Static -> buildAddStaticResourceBlock(resource, location.uri, generatedNames)
    is ResourceLocation.Template -> buildAddTemplateResourceBlock(resource, location.uriTemplate, generatedNames)
}
```

And update `buildResourceRegistrationFunction`:

```kotlin
private fun buildResourceRegistrationFunction(
    resource: ResourceFunction,
    generatedNames: GeneratedResourceNames,
): FunSpec = FunSpec.builder(generatedNames.registrationFunctionName(resource))
    .receiver(serverType)
    .addCode(buildAddResourceBlock(resource, generatedNames))
    .build()
```

- [ ] **Step 2: Update `buildAddTemplateResourceBlock` to inject context parameters**

Similarly, after the URI template variable extraction and the function call:

Replace:
```kotlin
code.add("val result = %L.%L(\n", resource.packageName, resource.functionName)
code.indent()
resource.parameters.forEach { parameter ->
    code.addStatement("%N = %N,", parameter.name, parameter.name)
}
code.unindent()
code.add(")\n")
```

with:
```kotlin
code.add("val result = %L.%L(\n", resource.packageName, resource.functionName)
code.indent()
resource.parameters.forEach { parameter ->
    code.addStatement("%N = %N,", parameter.name, parameter.name)
}
resource.contextParameters.forEach { contextParameter ->
    code.addStatement(
        "%N = %L,",
        contextParameter.name,
        contextParameter.type.codeBlock(generatedNames.registrationFunctionName(resource)),
    )
}
code.unindent()
code.add(")\n")
```

Update the function signature to accept `generatedNames`:

```kotlin
private fun buildAddTemplateResourceBlock(resource: ResourceFunction, uriTemplate: String, generatedNames: GeneratedResourceNames): CodeBlock
```

- [ ] **Step 3: Update `buildAddPromptBlock` to inject context parameters**

Similarly update the prompt function call. Replace:

```kotlin
code.add("val result = %L.%L(\n", prompt.packageName, prompt.functionName)
code.indent()
prompt.parameters.forEach { parameter ->
    code.addStatement("%N = %N,", parameter.name, parameter.name)
}
code.unindent()
code.add(")\n")
```

with:

```kotlin
code.add("val result = %L.%L(\n", prompt.packageName, prompt.functionName)
code.indent()
prompt.parameters.forEach { parameter ->
    code.addStatement("%N = %N,", parameter.name, parameter.name)
}
prompt.contextParameters.forEach { contextParameter ->
    code.addStatement(
        "%N = %L,",
        contextParameter.name,
        contextParameter.type.codeBlock(generatedNames.registrationFunctionName(prompt)),
    )
}
code.unindent()
code.add(")\n")
```

Also update `buildPromptRegistrationFunction` and `buildAddPromptBlock` signatures to accept `generatedNames`:

```kotlin
private fun buildPromptRegistrationFunction(
    prompt: PromptFunction,
    generatedNames: GeneratedPromptNames,
): FunSpec = FunSpec.builder(generatedNames.registrationFunctionName(prompt))
    .receiver(serverType)
    .addCode(buildAddPromptBlock(prompt, generatedNames))
    .build()

private fun buildAddPromptBlock(prompt: PromptFunction, generatedNames: GeneratedPromptNames): CodeBlock
```

- [ ] **Step 4: Add test for resource with context injection**

```kotlin
@Test
fun `generates static resource registration with context injection`() {
    val generated = ToolCodeGenerator.render(
        tools = emptyList(),
        resources = listOf(
            ResourceFunction(
                packageName = "com.example.resources",
                functionName = "config",
                resourceName = "config",
                description = "Configuration.",
                location = ResourceLocation.Static("file:///config"),
                mimeType = "application/json",
                isSuspend = false,
                parameters = emptyList(),
                contextParameters = listOf(
                    ContextParameter(name = "conn", type = ContextParameterType.ClientConnection),
                    ContextParameter(name = "req", type = ContextParameterType.ReadResourceRequest),
                ),
                returnType = ResourceReturnType.TextType,
            ),
        ),
        prompts = emptyList(),
    )

    assertTrue(generated.contains("conn = this,"), generated)
    assertTrue(generated.contains("req = request,"), generated)
}
```

- [ ] **Step 5: Add test for prompt with context injection**

```kotlin
@Test
fun `generates prompt registration with context injection`() {
    val generated = ToolCodeGenerator.render(
        tools = emptyList(),
        resources = emptyList(),
        prompts = listOf(
            PromptFunction(
                packageName = "com.example.prompts",
                functionName = "summarize",
                promptName = "summarize",
                description = "Summarize.",
                isSuspend = false,
                parameters = emptyList(),
                contextParameters = listOf(
                    ContextParameter(name = "conn", type = ContextParameterType.ClientConnection),
                    ContextParameter(name = "req", type = ContextParameterType.GetPromptRequest),
                    ContextParameter(name = "srv", type = ContextParameterType.Server),
                ),
                returnType = PromptReturnType.TextType,
            ),
        ),
    )

    assertTrue(generated.contains("conn = this,"), generated)
    assertTrue(generated.contains("req = request,"), generated)
    assertTrue(generated.contains("srv = this@registerSummarizePrompt,"), generated)
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :mcp-tool-ksp:test -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: generate context injection for @McpResource and @McpPrompt"
```

---

### Task 6: Update `McpToolProcessor.kt` for context parameter flow

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/McpToolProcessor.kt`

No logic changes needed — the processor already passes `ToolFunction`, `ResourceFunction`, and `PromptFunction` to the code generator, and those now contain `contextParameters`. The processor just needs to continue working correctly.

- [ ] **Step 1: Run full KSP test suite to verify nothing is broken**

Run: `./gradlew :mcp-tool-ksp:test -q`
Expected: PASS

- [ ] **Step 2: Commit if any adjustments were needed**

Only commit if changes were required.

---

### Task 7: Full integration test — build the project and verify generated code

**Files:**
- No new files; verify existing `essential-mcp` module builds correctly

- [ ] **Step 1: Run the full build**

Run: `./gradlew :essential-mcp:jvmJar -q`
Expected: PASS — the KSP processor generates code for existing tools (which have no context params), so `contextParameters = emptyList()` in the generated data should be backward compatible.

- [ ] **Step 2: Inspect the generated code to confirm no regression**

Run: `cat essential-mcp/build/generated/ksp/metadata/commonMain/kotlin/io/github/qingshu/mcptool/generated/GeneratedMcpTools.kt | head -30`

Verify it still contains `registerGeneratedMcpDeclarations`, `registerGeneratedMcpTools`, etc. with no unexpected changes.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git commit --allow-empty -m "chore: verify context injection feature builds and tests pass"
```

---

### Task 8: Apply Spotless formatting and final commit

**Files:**
- All modified files

- [ ] **Step 1: Run Spotless**

Run: `./gradlew spotlessApply -q`

- [ ] **Step 2: Stage and commit formatted files**

```bash
git add -A
git commit -m "style: apply spotless formatting"
```

- [ ] **Step 3: Final verification — run all tests**

Run: `./gradlew test -q`
Expected: PASS
