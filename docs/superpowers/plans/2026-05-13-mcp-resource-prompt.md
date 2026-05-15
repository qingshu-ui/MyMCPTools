# MCP Resource and Prompt Annotation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add comprehensive `@McpResource` and `@McpPrompt` KSP support alongside the existing `@McpTool` pipeline.

**Architecture:** Upgrade the MCP Kotlin SDK to 0.12.0, add resource/prompt annotations in the annotation module, extend the KSP processor with resource and prompt models/validators/generators, and expose generated aggregate registration through the existing server registry. Keep existing tool behavior unchanged while sharing small helpers for parameters, names, and conversions.

**Tech Stack:** Kotlin Multiplatform, KSP, KotlinPoet, MCP Kotlin SDK 0.12.0, kotlin.test, Gradle, Spotless.

---

## File Structure

- Modify `gradle/libs.versions.toml` — set MCP Kotlin SDK version to `0.12.0`.
- Create `mcp-tool-annotations/src/commonMain/kotlin/McpResource.kt` — `@McpResource` annotation.
- Create `mcp-tool-annotations/src/commonMain/kotlin/McpPrompt.kt` — `@McpPrompt` annotation.
- Create `mcp-tool-annotations/src/commonMain/kotlin/PromptParam.kt` — prompt argument annotation using the existing `Required` enum.
- Modify `mcp-tool-ksp/src/main/kotlin/ToolModels.kt` — add `ResourceFunction`, `PromptFunction`, shared return-type models, `ResourceLocation`, and `extractUriTemplateVariables`.
- Modify `mcp-tool-ksp/src/main/kotlin/ToolValidator.kt` — keep tool validation intact and expose the existing annotation argument helper for resource/prompt validation.
- Create `mcp-tool-ksp/src/main/kotlin/ResourcePromptValidator.kt` — validate `@McpResource`, `@McpPrompt`, `@PromptParam`, URI templates, and return types.
- Modify `mcp-tool-ksp/src/main/kotlin/McpToolProcessor.kt` — collect tool/resource/prompt symbols, validate duplicates, and call the expanded generator.
- Modify `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt` — generate tools, resources, prompts, and aggregate registration functions.
- Modify `essential-mcp/src/commonMain/kotlin/McpTools.kt` — call `registerGeneratedMcpDeclarations()`.
- Modify `essential-mcp/src/commonMain/kotlin/Server.kt` — enable resource and prompt capabilities when compile errors show SDK 0.12.0 requires explicit capability flags.
- Modify `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt` — keep existing tool generator tests passing and add generation tests for resources/prompts.
- Modify `mcp-tool-ksp/src/test/kotlin/ToolValidatorTest.kt` — add focused model/validator tests for shared helpers.
- Create `mcp-tool-ksp/src/test/kotlin/ResourcePromptValidatorTest.kt` — resource/prompt validation unit tests.
- Modify `essential-mcp/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt` — add compile references for generated resource and prompt functions.

Do not commit during execution unless the user explicitly asks for commits. Commit checkpoint steps below are written for authorized commit sessions only.

---

### Task 1: Upgrade MCP SDK to 0.12.0 and verify baseline

**Files:**
- Modify: `gradle/libs.versions.toml:9`

- [ ] **Step 1: Change the MCP SDK version**

Edit `gradle/libs.versions.toml` so the MCP version is exactly:

```toml
mcp = "0.12.0"
```

- [ ] **Step 2: Run KSP tests to expose SDK API changes**

Run:

```bash
./gradlew :mcp-tool-ksp:test
```

Expected: PASS, or FAIL only because MCP SDK 0.12.0 changed type or method names used by the existing generator tests. If it fails for dependency resolution, stop and inspect the dependency coordinates before continuing.

- [ ] **Step 3: Run main compile to expose SDK capability changes**

Run:

```bash
./gradlew :essential-mcp:compileKotlinMetadata
```

Expected: PASS, or FAIL with concrete SDK API changes in current server setup. Fix only existing SDK migration breakage before adding new annotations.

- [ ] **Step 4: Commit checkpoint if commits were explicitly authorized**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: update MCP Kotlin SDK to 0.12.0"
```

---

### Task 2: Add resource and prompt annotations

**Files:**
- Create: `mcp-tool-annotations/src/commonMain/kotlin/McpResource.kt`
- Create: `mcp-tool-annotations/src/commonMain/kotlin/McpPrompt.kt`
- Create: `mcp-tool-annotations/src/commonMain/kotlin/PromptParam.kt`
- Test: `essential-mcp/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt`

- [ ] **Step 1: Write a compile test that imports the new annotations**

Append this test to `GeneratedMcpToolsCompileTest.kt`:

```kotlin
@Test
fun resourceAndPromptAnnotationsAreVisibleToCommonCode() {
    val resource = McpResource::class.simpleName
    val prompt = McpPrompt::class.simpleName
    val param = PromptParam::class.simpleName

    assertEquals("McpResource", resource)
    assertEquals("McpPrompt", prompt)
    assertEquals("PromptParam", param)
}
```

Add imports:

```kotlin
import io.github.qingshu.mcptool.annotations.McpPrompt
import io.github.qingshu.mcptool.annotations.McpResource
import io.github.qingshu.mcptool.annotations.PromptParam
import kotlin.test.assertEquals
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :essential-mcp:compileTestKotlinJvm
```

Expected: FAIL with unresolved references for `McpResource`, `McpPrompt`, and `PromptParam`.

- [ ] **Step 3: Add `@McpResource`**

Create `mcp-tool-annotations/src/commonMain/kotlin/McpResource.kt`:

```kotlin
package io.github.qingshu.mcptool.annotations

/**
 * Marks a top-level function as an MCP resource definition.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class McpResource(
    public val name: String,
    public val description: String,
    public val uri: String = "",
    public val uriTemplate: String = "",
    public val mimeType: String = "text/plain",
)
```

- [ ] **Step 4: Add `@McpPrompt`**

Create `mcp-tool-annotations/src/commonMain/kotlin/McpPrompt.kt`:

```kotlin
package io.github.qingshu.mcptool.annotations

/**
 * Marks a top-level function as an MCP prompt definition.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class McpPrompt(
    public val name: String,
    public val description: String,
)
```

- [ ] **Step 5: Add `@PromptParam`**

Create `mcp-tool-annotations/src/commonMain/kotlin/PromptParam.kt`:

```kotlin
package io.github.qingshu.mcptool.annotations

/**
 * Documents an argument exposed in a generated MCP prompt schema.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class PromptParam(
    public val description: String,
    public val required: Required = Required.UNSPECIFIED,
    public val name: String = "",
)
```

- [ ] **Step 6: Run the compile test and verify it passes**

Run:

```bash
./gradlew :essential-mcp:compileTestKotlinJvm
```

Expected: PASS.

- [ ] **Step 7: Commit checkpoint if commits were explicitly authorized**

```bash
git add mcp-tool-annotations/src/commonMain/kotlin/McpResource.kt mcp-tool-annotations/src/commonMain/kotlin/McpPrompt.kt mcp-tool-annotations/src/commonMain/kotlin/PromptParam.kt essential-mcp/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt
git commit -m "feat: add MCP resource and prompt annotations"
```

---

### Task 3: Add models for resources, prompts, and URI templates

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolModels.kt`
- Test: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Add failing model tests**

Add these tests to `ToolModelsTest`:

```kotlin
@Test
fun `extracts URI template variables in declaration order`() {
    assertEquals(listOf("artist", "track"), extractUriTemplateVariables("audio://{artist}/tracks/{track}"))
}

@Test
fun `ignores duplicate URI template variables after first occurrence`() {
    assertEquals(listOf("id"), extractUriTemplateVariables("audio://tracks/{id}/related/{id}"))
}

@Test
fun `resource location identifies static and template resources`() {
    assertEquals(ResourceLocation.Static("file:///config"), ResourceLocation.from(uri = "file:///config", uriTemplate = ""))
    assertEquals(ResourceLocation.Template("audio://tracks/{id}"), ResourceLocation.from(uri = "", uriTemplate = "audio://tracks/{id}"))
    assertNull(ResourceLocation.from(uri = "", uriTemplate = ""))
    assertNull(ResourceLocation.from(uri = "file:///config", uriTemplate = "audio://tracks/{id}"))
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: FAIL with unresolved references for `extractUriTemplateVariables` and `ResourceLocation`.

- [ ] **Step 3: Add resource and prompt models**

Append these declarations to `ToolModels.kt`:

```kotlin
data class ResourceFunction(
    val packageName: String,
    val functionName: String,
    val resourceName: String,
    val description: String,
    val location: ResourceLocation,
    val mimeType: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val returnType: ResourceReturnType,
)

data class PromptFunction(
    val packageName: String,
    val functionName: String,
    val promptName: String,
    val description: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val returnType: PromptReturnType,
)

sealed class ResourceLocation {
    data class Static(val uri: String) : ResourceLocation()
    data class Template(val uriTemplate: String) : ResourceLocation()

    companion object {
        fun from(uri: String, uriTemplate: String): ResourceLocation? = when {
            uri.isNotBlank() && uriTemplate.isBlank() -> Static(uri)
            uri.isBlank() && uriTemplate.isNotBlank() -> Template(uriTemplate)
            else -> null
        }
    }
}

sealed class ResourceReturnType {
    data object TextType : ResourceReturnType()
    data object TextResourceContentsType : ResourceReturnType()
    data object BlobResourceContentsType : ResourceReturnType()
    data object ReadResourceResultType : ResourceReturnType()
}

sealed class PromptReturnType {
    data object TextType : PromptReturnType()
    data object PromptMessageType : PromptReturnType()
    data object PromptMessageListType : PromptReturnType()
    data object GetPromptResultType : PromptReturnType()
}

fun extractUriTemplateVariables(uriTemplate: String): List<String> {
    val matches = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)}").findAll(uriTemplate)
    return matches.map { it.groupValues[1] }.distinct().toList()
}
```

- [ ] **Step 4: Run the model tests and verify they pass**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: PASS.

- [ ] **Step 5: Commit checkpoint if commits were explicitly authorized**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolModels.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: model MCP resources and prompts"
```

---

### Task 4: Validate resource and prompt declarations

**Files:**
- Create: `mcp-tool-ksp/src/main/kotlin/ResourcePromptValidator.kt`
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolValidator.kt`
- Create: `mcp-tool-ksp/src/test/kotlin/ResourcePromptValidatorTest.kt`

- [ ] **Step 1: Add pure validation tests for resource locations and template parameters**

Create `ResourcePromptValidatorTest.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import kotlin.test.Test
import kotlin.test.assertEquals

class ResourcePromptValidatorTest {
    @Test
    fun `missing resource location is invalid`() {
        assertEquals("@McpResource must specify exactly one of uri or uriTemplate.", validateResourceLocation(uri = "", uriTemplate = ""))
    }

    @Test
    fun `resource cannot specify both uri and uri template`() {
        assertEquals(
            "@McpResource must specify exactly one of uri or uriTemplate.",
            validateResourceLocation(uri = "file:///config", uriTemplate = "audio://tracks/{id}"),
        )
    }

    @Test
    fun `template variables must match parameter names`() {
        assertEquals(
            "URI template variable(s) missing matching function parameters: trackId",
            validateUriTemplateParameters(uriTemplate = "audio://tracks/{trackId}", parameterNames = setOf("id")),
        )
    }

    @Test
    fun `static resources cannot declare parameters`() {
        assertEquals(
            "Static @McpResource functions must not declare parameters.",
            validateStaticResourceParameters(parameterNames = listOf("id")),
        )
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ResourcePromptValidatorTest"
```

Expected: FAIL with unresolved validation functions.

- [ ] **Step 3: Add resource/prompt validation constants and pure helpers**

Create `ResourcePromptValidator.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier

private const val MCP_RESOURCE_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpResource"
private const val MCP_PROMPT_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpPrompt"
private const val PROMPT_PARAM_ANNOTATION = "io.github.qingshu.mcptool.annotations.PromptParam"
private const val READ_RESOURCE_RESULT = "io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult"
private const val TEXT_RESOURCE_CONTENTS = "io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents"
private const val BLOB_RESOURCE_CONTENTS = "io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents"
private const val GET_PROMPT_RESULT = "io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult"
private const val PROMPT_MESSAGE = "io.modelcontextprotocol.kotlin.sdk.types.PromptMessage"
private const val PROMPT_MESSAGE_LIST = "kotlin.collections.List"

internal fun validateResourceLocation(uri: String, uriTemplate: String): String? =
    if (ResourceLocation.from(uri, uriTemplate) == null) "@McpResource must specify exactly one of uri or uriTemplate." else null

internal fun validateUriTemplateParameters(uriTemplate: String, parameterNames: Set<String>): String? {
    val missing = extractUriTemplateVariables(uriTemplate).filterNot(parameterNames::contains)
    return if (missing.isEmpty()) null else "URI template variable(s) missing matching function parameters: ${missing.joinToString()}"
}

internal fun validateStaticResourceParameters(parameterNames: List<String>): String? =
    if (parameterNames.isEmpty()) null else "Static @McpResource functions must not declare parameters."
```

- [ ] **Step 4: Expose annotation argument helper to the new validator**

In `ToolValidator.kt`, change:

```kotlin
private fun <T> KSAnnotation.argumentValue(name: String): T? = arguments.firstOrNull { it.name?.asString() == name }?.value as? T
```

to:

```kotlin
@Suppress("UNCHECKED_CAST")
internal fun <T> KSAnnotation.argumentValue(name: String): T? = arguments.firstOrNull { it.name?.asString() == name }?.value as? T
```

Keep only one `@Suppress("UNCHECKED_CAST")` annotation on that function.

- [ ] **Step 5: Add KSP conversion functions**

Append these functions to `ResourcePromptValidator.kt` and keep existing imports:

```kotlin
internal fun KSFunctionDeclaration.toResourceFunctionOrNull(logger: KSPLogger): ResourceFunction? {
    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == MCP_RESOURCE_ANNOTATION
    } ?: return null

    if (parentDeclaration != null) {
        logger.error("@McpResource is only supported on top-level functions in v1.", this)
        return null
    }

    val name = annotation.argumentValue<String>("name").orEmpty()
    val description = annotation.argumentValue<String>("description").orEmpty()
    val uri = annotation.argumentValue<String>("uri").orEmpty()
    val uriTemplate = annotation.argumentValue<String>("uriTemplate").orEmpty()
    val mimeType = annotation.argumentValue<String>("mimeType").orEmpty().ifBlank { "text/plain" }
    val location = ResourceLocation.from(uri, uriTemplate)

    if (name.isBlank()) {
        logger.error("@McpResource name must not be blank.", this)
        return null
    }
    if (name.normalizedToolFunctionNameComponent().isBlank()) {
        logger.error("@McpResource name must contain at least one letter or digit usable in generated Kotlin function names.", this)
        return null
    }
    if (description.isBlank()) {
        logger.error("@McpResource description must not be blank.", this)
        return null
    }
    validateResourceLocation(uri, uriTemplate)?.let { message ->
        logger.error(message, this)
        return null
    }

    val parameters = parameters.mapNotNull { parameter -> parameter.toUriTemplateParameterOrNull(logger) }
    if (parameters.size != this.parameters.size) return null

    when (location) {
        is ResourceLocation.Static -> validateStaticResourceParameters(parameters.map { it.name })
        is ResourceLocation.Template -> validateUriTemplateParameters(location.uriTemplate, parameters.map { it.name }.toSet())
        null -> null
    }?.let { message ->
        logger.error(message, this)
        return null
    }

    val returnType = resolveResourceReturnType(logger) ?: return null

    return ResourceFunction(
        packageName = packageName.asString(),
        functionName = simpleName.asString(),
        resourceName = name,
        description = description,
        location = location!!,
        mimeType = mimeType,
        isSuspend = modifiers.contains(Modifier.SUSPEND),
        parameters = parameters,
        returnType = returnType,
    )
}

internal fun KSFunctionDeclaration.toPromptFunctionOrNull(logger: KSPLogger): PromptFunction? {
    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == MCP_PROMPT_ANNOTATION
    } ?: return null

    if (parentDeclaration != null) {
        logger.error("@McpPrompt is only supported on top-level functions in v1.", this)
        return null
    }

    val name = annotation.argumentValue<String>("name").orEmpty()
    val description = annotation.argumentValue<String>("description").orEmpty()

    if (name.isBlank()) {
        logger.error("@McpPrompt name must not be blank.", this)
        return null
    }
    if (name.normalizedToolFunctionNameComponent().isBlank()) {
        logger.error("@McpPrompt name must contain at least one letter or digit usable in generated Kotlin function names.", this)
        return null
    }
    if (description.isBlank()) {
        logger.error("@McpPrompt description must not be blank.", this)
        return null
    }

    val parameters = parameters.mapNotNull { parameter -> parameter.toPromptParameterOrNull(logger) }
    if (parameters.size != this.parameters.size) return null
    if (!validateUniqueSchemaNames(parameters, name, logger, this)) return null

    val returnType = resolvePromptReturnType(logger) ?: return null

    return PromptFunction(
        packageName = packageName.asString(),
        functionName = simpleName.asString(),
        promptName = name,
        description = description,
        isSuspend = modifiers.contains(Modifier.SUSPEND),
        parameters = parameters,
        returnType = returnType,
    )
}
```

- [ ] **Step 6: Add parameter and return-type helpers**

Append these helpers to `ResourcePromptValidator.kt`:

```kotlin
private fun KSValueParameter.toUriTemplateParameterOrNull(logger: KSPLogger): ToolParameter? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpResource parameters must have stable names.", this)
        return null
    }
    val resolvedType = type.resolve()
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()
    val parameterType = ParameterType.fromQualifiedName(qualifiedType)
    if (parameterType == null) {
        logger.error("Unsupported URI template parameter type '$qualifiedType' for '$parameterName'. Supported types: String, Int, Long, Double, Boolean.", this)
        return null
    }
    return ToolParameter(
        name = parameterName,
        schemaName = parameterName,
        description = parameterName,
        type = parameterType,
        nullable = resolvedType.isMarkedNullable,
        hasDefault = hasDefault,
        required = inferRequiredness(resolvedType.isMarkedNullable, hasDefault, io.github.qingshu.mcptool.annotations.Required.UNSPECIFIED),
    )
}

private fun KSValueParameter.toPromptParameterOrNull(logger: KSPLogger): ToolParameter? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpPrompt parameters must have stable names.", this)
        return null
    }
    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == PROMPT_PARAM_ANNOTATION
    }
    if (annotation == null) {
        logger.error("Parameter '$parameterName' must be annotated with @PromptParam.", this)
        return null
    }
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
        logger.error("Unsupported @PromptParam type '$qualifiedType' for '$parameterName'. Supported types: String, Int, Long, Double, Boolean.", this)
        return null
    }
    val explicitRequired = annotation.requiredArgumentValue() ?: io.github.qingshu.mcptool.annotations.Required.UNSPECIFIED
    val required = try {
        inferRequiredness(resolvedType.isMarkedNullable, hasDefault, explicitRequired)
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid requiredness for parameter '$parameterName': ${e.message}", this)
        return null
    }
    return ToolParameter(parameterName, schemaName, description, parameterType, resolvedType.isMarkedNullable, hasDefault, required)
}

private fun KSFunctionDeclaration.resolveResourceReturnType(logger: KSPLogger): ResourceReturnType? {
    val resolved = returnType?.resolve()
    val qualifiedName = resolved?.declaration?.qualifiedName?.asString() ?: "kotlin.Unit"
    return when (qualifiedName) {
        "kotlin.String" -> ResourceReturnType.TextType
        TEXT_RESOURCE_CONTENTS -> ResourceReturnType.TextResourceContentsType
        BLOB_RESOURCE_CONTENTS -> ResourceReturnType.BlobResourceContentsType
        READ_RESOURCE_RESULT -> ResourceReturnType.ReadResourceResultType
        else -> {
            logger.error("Unsupported @McpResource return type '$qualifiedName'. Supported returns: String, TextResourceContents, BlobResourceContents, ReadResourceResult.", this)
            null
        }
    }
}

private fun KSFunctionDeclaration.resolvePromptReturnType(logger: KSPLogger): PromptReturnType? {
    val resolved = returnType?.resolve()
    val qualifiedName = resolved?.declaration?.qualifiedName?.asString() ?: "kotlin.Unit"
    val typeArguments = resolved?.arguments.orEmpty()
    return when {
        qualifiedName == "kotlin.String" -> PromptReturnType.TextType
        qualifiedName == PROMPT_MESSAGE -> PromptReturnType.PromptMessageType
        qualifiedName == GET_PROMPT_RESULT -> PromptReturnType.GetPromptResultType
        qualifiedName == PROMPT_MESSAGE_LIST && typeArguments.firstOrNull()?.type?.resolve()?.declaration?.qualifiedName?.asString() == PROMPT_MESSAGE -> PromptReturnType.PromptMessageListType
        else -> {
            logger.error("Unsupported @McpPrompt return type '$qualifiedName'. Supported returns: String, PromptMessage, List<PromptMessage>, GetPromptResult.", this)
            null
        }
    }
}
```

- [ ] **Step 7: Run validator tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ResourcePromptValidatorTest" --tests "io.github.qingshu.mcptool.ksp.ToolValidatorTest"
```

Expected: PASS.

- [ ] **Step 8: Commit checkpoint if commits were explicitly authorized**

```bash
git add mcp-tool-ksp/src/main/kotlin/ResourcePromptValidator.kt mcp-tool-ksp/src/main/kotlin/ToolValidator.kt mcp-tool-ksp/src/test/kotlin/ResourcePromptValidatorTest.kt
git commit -m "feat: validate MCP resources and prompts"
```

---

### Task 5: Extend the processor to collect resources and prompts

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/McpToolProcessor.kt`
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt`
- Test: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Add a failing aggregate generation test**

Add this test to `ToolModelsTest`:

```kotlin
@Test
fun `generates aggregate declaration registration function`() {
    val generated = ToolCodeGenerator.render(
        tools = listOf(simpleTextTool(toolName = "echo", functionName = "echo")),
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
                returnType = ResourceReturnType.TextType,
            ),
        ),
        prompts = listOf(
            PromptFunction(
                packageName = "com.example.prompts",
                functionName = "summarize",
                promptName = "summarize",
                description = "Summarize text.",
                isSuspend = false,
                parameters = emptyList(),
                returnType = PromptReturnType.TextType,
            ),
        ),
    )

    assertTrue(generated.contains("public fun Server.registerGeneratedMcpDeclarations()"), generated)
    assertTrue(generated.contains("registerGeneratedMcpTools()"), generated)
    assertTrue(generated.contains("registerGeneratedMcpResources()"), generated)
    assertTrue(generated.contains("registerGeneratedMcpPrompts()"), generated)
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates aggregate declaration registration function"
```

Expected: FAIL because `ToolCodeGenerator.render` does not accept resources/prompts yet.

- [ ] **Step 3: Expand `ToolCodeGenerator.generate` and `render` signatures**

Change `ToolCodeGenerator` entry points to:

```kotlin
fun generate(
    tools: List<ToolFunction>,
    resources: List<ResourceFunction>,
    prompts: List<PromptFunction>,
) {
    val rendered = render(tools = tools, resources = resources, prompts = prompts)
    context.codeGenerator
        .createNewFile(
            dependencies = Dependencies(aggregating = true),
            packageName = GENERATED_PACKAGE,
            fileName = GENERATED_FILE_NAME,
        ).use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write(rendered)
            }
        }
}

companion object {
    fun render(
        tools: List<ToolFunction>,
        resources: List<ResourceFunction> = emptyList(),
        prompts: List<PromptFunction> = emptyList(),
    ): String = buildFileSpec(
        tools = tools.sortedBy { it.toolName },
        resources = resources.sortedBy { it.resourceName },
        prompts = prompts.sortedBy { it.promptName },
    ).toString()
}
```

Update existing tests that call `ToolCodeGenerator.render(listOf(...))` to use named argument `tools = listOf(...)`.

- [ ] **Step 4: Add aggregate declaration functions**

Add generated functions in `ToolCodeGenerator`:

```kotlin
private fun buildDeclarationAggregateFunction(): FunSpec = FunSpec.builder("registerGeneratedMcpDeclarations")
    .receiver(serverType)
    .addStatement("registerGeneratedMcpTools()")
    .addStatement("registerGeneratedMcpResources()")
    .addStatement("registerGeneratedMcpPrompts()")
    .build()

private fun buildEmptyAggregateFunction(name: String): FunSpec = FunSpec.builder(name)
    .receiver(serverType)
    .build()
```

In `buildFileSpec`, add `buildDeclarationAggregateFunction()` before the per-kind aggregate functions. Use `buildEmptyAggregateFunction("registerGeneratedMcpResources")` and `buildEmptyAggregateFunction("registerGeneratedMcpPrompts")` until resource/prompt generation is implemented.

- [ ] **Step 5: Extend `McpToolProcessor` symbol collection**

Replace single annotation collection with three lists:

```kotlin
private const val MCP_RESOURCE_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpResource"
private const val MCP_PROMPT_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpPrompt"
```

Inside `process`:

```kotlin
val toolSymbols = resolver.getSymbolsWithAnnotation(MCP_TOOL_ANNOTATION).filterIsInstance<KSFunctionDeclaration>().toList()
val resourceSymbols = resolver.getSymbolsWithAnnotation(MCP_RESOURCE_ANNOTATION).filterIsInstance<KSFunctionDeclaration>().toList()
val promptSymbols = resolver.getSymbolsWithAnnotation(MCP_PROMPT_ANNOTATION).filterIsInstance<KSFunctionDeclaration>().toList()
val symbols = toolSymbols + resourceSymbols + promptSymbols
```

Add maps:

```kotlin
private val seenResources = linkedMapOf<String, ResourceDeclaration>()
private val seenPrompts = linkedMapOf<String, PromptDeclaration>()
```

Create declarations:

```kotlin
private data class ResourceDeclaration(val declaration: KSFunctionDeclaration, val resource: ResourceFunction)
private data class PromptDeclaration(val declaration: KSFunctionDeclaration, val prompt: PromptFunction)
```

Loop over `toolSymbols`, `resourceSymbols`, and `promptSymbols` separately so each calls the correct converter.

- [ ] **Step 6: Add duplicate validation per kind**

Add duplicate checks:

```kotlin
val duplicateResourceNames = seenResources.values.groupBy { it.resource.resourceName }.filterValues { it.size > 1 }
val duplicatePromptNames = seenPrompts.values.groupBy { it.prompt.promptName }.filterValues { it.size > 1 }
val duplicateResourceLocations = seenResources.values.groupBy { it.resource.location }.filterValues { it.size > 1 }
```

Log errors:

```kotlin
"Duplicate MCP resource name '$name'. Resource names must be unique."
"Duplicate MCP resource location '$location'. Resource URIs and URI templates must be unique."
"Duplicate MCP prompt name '$name'. Prompt names must be unique."
```

Generate only when all duplicate maps are empty. Call:

```kotlin
ToolCodeGenerator(context).generate(
    tools = seenTools.values.map { it.tool },
    resources = seenResources.values.map { it.resource },
    prompts = seenPrompts.values.map { it.prompt },
)
```

- [ ] **Step 7: Run processor/generator tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test
```

Expected: PASS.

- [ ] **Step 8: Commit checkpoint if commits were explicitly authorized**

```bash
git add mcp-tool-ksp/src/main/kotlin/McpToolProcessor.kt mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: collect MCP resources and prompts in KSP"
```

---

### Task 6: Generate static resources and prompts

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt`
- Modify: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Add failing static resource generation test**

Add this test to `ToolModelsTest`:

```kotlin
@Test
fun `generates static resource registration`() {
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
                returnType = ResourceReturnType.TextType,
            ),
        ),
        prompts = emptyList(),
    )

    assertTrue(generated.contains("public fun Server.registerGeneratedMcpResources()"), generated)
    assertTrue(generated.contains("addResource("), generated)
    assertTrue(generated.contains("uri = \"file:///config\""), generated)
    assertTrue(generated.contains("name = \"config\""), generated)
    assertTrue(generated.contains("mimeType = \"application/json\""), generated)
    assertTrue(generated.contains("TextResourceContents("), generated)
    assertTrue(generated.contains("text = result"), generated)
}
```

- [ ] **Step 2: Add failing prompt generation test**

Add this test to `ToolModelsTest`:

```kotlin
@Test
fun `generates prompt registration with arguments`() {
    val generated = ToolCodeGenerator.render(
        tools = emptyList(),
        resources = emptyList(),
        prompts = listOf(
            PromptFunction(
                packageName = "com.example.prompts",
                functionName = "summarize",
                promptName = "summarize_audio",
                description = "Summarize audio.",
                isSuspend = false,
                parameters = listOf(
                    ToolParameter(
                        name = "audioPath",
                        schemaName = "audio_path",
                        description = "Audio path",
                        type = ParameterType.StringType,
                        nullable = false,
                        hasDefault = false,
                        required = true,
                    ),
                ),
                returnType = PromptReturnType.TextType,
            ),
        ),
    )

    assertTrue(generated.contains("public fun Server.registerGeneratedMcpPrompts()"), generated)
    assertTrue(generated.contains("addPrompt("), generated)
    assertTrue(generated.contains("name = \"summarize_audio\""), generated)
    assertTrue(generated.contains("PromptArgument("), generated)
    assertTrue(generated.contains("name = \"audio_path\""), generated)
    assertTrue(generated.contains("required = true"), generated)
    assertTrue(generated.contains("val audioPath = arguments?.get(\"audio_path\")"), generated)
    assertTrue(generated.contains("GetPromptResult("), generated)
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: FAIL because resource and prompt generation is not implemented.

- [ ] **Step 4: Add SDK imports to generated file**

In `buildFileSpec`, expand imports:

```kotlin
.addImport(
    "io.modelcontextprotocol.kotlin.sdk.types",
    "BlobResourceContents",
    "CallToolResult",
    "GetPromptResult",
    "PromptArgument",
    "PromptMessage",
    "ReadResourceResult",
    "Role",
    "TextContent",
    "TextResourceContents",
    "ToolSchema",
)
```

Keep JSON imports for tool parsing and add prompt string parsing only when needed.

- [ ] **Step 5: Add generated name support for resources and prompts**

Add private name classes next to `GeneratedToolNames`:

```kotlin
private class GeneratedResourceNames private constructor(private val namesByResource: Map<ResourceFunction, ResourceNames>) {
    fun registrationFunctionName(resource: ResourceFunction): String = namesByResource.getValue(resource).registration
    fun invocationHelperName(resource: ResourceFunction): String = namesByResource.getValue(resource).invocation

    companion object {
        fun create(resources: List<ResourceFunction>): GeneratedResourceNames {
            val baseNameCounts = resources.groupingBy { it.resourceName.normalizedToolFunctionNameComponent() }.eachCount()
            val indicesByBaseName = linkedMapOf<String, Int>()
            val namesByResource = LinkedHashMap<ResourceFunction, ResourceNames>()
            resources.forEach { resource ->
                val baseName = resource.resourceName.normalizedToolFunctionNameComponent()
                val index = indicesByBaseName.compute(baseName) { _, count -> (count ?: 0) + 1 }!!
                val uniqueName = if (baseNameCounts.getValue(baseName) == 1) baseName else "$baseName$index"
                namesByResource[resource] = ResourceNames("register${uniqueName}Resource", "invoke${uniqueName}Resource")
            }
            return GeneratedResourceNames(namesByResource)
        }
    }
}

private data class ResourceNames(val registration: String, val invocation: String)
```

Add equivalent `GeneratedPromptNames` with suffixes `Prompt`.

- [ ] **Step 6: Generate static resource registration**

Add functions shaped like:

```kotlin
private fun buildResourceAggregateFunction(resources: List<ResourceFunction>, generatedNames: GeneratedResourceNames): FunSpec =
    FunSpec.builder("registerGeneratedMcpResources")
        .receiver(serverType)
        .apply { resources.forEach { resource -> addStatement("%N()", generatedNames.registrationFunctionName(resource)) } }
        .build()

private fun buildStaticResourceRegistrationFunction(resource: ResourceFunction, generatedNames: GeneratedResourceNames): FunSpec =
    FunSpec.builder(generatedNames.registrationFunctionName(resource))
        .receiver(serverType)
        .addCode(buildStaticResourceBlock(resource))
        .build()
```

The generated code for a `String` resource must have this shape:

```kotlin
addResource(
    uri = "file:///config",
    name = "config",
    description = "Configuration.",
    mimeType = "application/json",
) { request ->
    try {
        val result = com.example.resources.config()
        return@addResource ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    uri = request.params.uri,
                    mimeType = "application/json",
                    text = result,
                ),
            ),
        )
    } catch (exception: Exception) {
        return@addResource ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    uri = request.params.uri,
                    mimeType = "text/plain",
                    text = exception.message ?: "Resource failed",
                ),
            ),
        )
    }
}
```

- [ ] **Step 7: Generate prompt registration**

Add functions shaped like:

```kotlin
private fun buildPromptAggregateFunction(prompts: List<PromptFunction>, generatedNames: GeneratedPromptNames): FunSpec =
    FunSpec.builder("registerGeneratedMcpPrompts")
        .receiver(serverType)
        .apply { prompts.forEach { prompt -> addStatement("%N()", generatedNames.registrationFunctionName(prompt)) } }
        .build()
```

The generated code for a `String` prompt must have this shape:

```kotlin
addPrompt(
    name = "summarize_audio",
    description = "Summarize audio.",
    arguments = listOf(
        PromptArgument(
            name = "audio_path",
            description = "Audio path",
            required = true,
        ),
    ),
) { request ->
    try {
        val arguments = request.params.arguments
        val audioPathPresent = arguments?.containsKey("audio_path") == true
        val audioPath = arguments?.get("audio_path")
        if (audioPath == null) {
            return@addPrompt promptErrorResult("Missing required argument: audio_path")
        }
        val result = com.example.prompts.summarize(audioPath = audioPath)
        return@addPrompt GetPromptResult(
            description = "Summarize audio.",
            messages = listOf(PromptMessage(role = Role.USER, content = TextContent(result))),
        )
    } catch (exception: Exception) {
        return@addPrompt promptErrorResult(exception.message ?: "Prompt failed")
    }
}
```

- [ ] **Step 8: Add prompt error helper generation**

Generate this helper when prompts are present:

```kotlin
private fun promptErrorResult(message: String): GetPromptResult = GetPromptResult(
    description = "Prompt failed",
    messages = listOf(PromptMessage(role = Role.USER, content = TextContent(message))),
)
```

- [ ] **Step 9: Run generator tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: PASS.

- [ ] **Step 10: Commit checkpoint if commits were explicitly authorized**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: generate static MCP resources and prompts"
```

---

### Task 7: Generate dynamic URI-template resources

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt`
- Modify: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Add failing dynamic resource generation test**

Add this test to `ToolModelsTest`:

```kotlin
@Test
fun `generates URI template resource registration`() {
    val generated = ToolCodeGenerator.render(
        tools = emptyList(),
        resources = listOf(
            ResourceFunction(
                packageName = "com.example.resources",
                functionName = "trackMetadata",
                resourceName = "track_metadata",
                description = "Track metadata.",
                location = ResourceLocation.Template("audio://tracks/{id}/metadata"),
                mimeType = "application/json",
                isSuspend = false,
                parameters = listOf(
                    ToolParameter(
                        name = "id",
                        schemaName = "id",
                        description = "id",
                        type = ParameterType.StringType,
                        nullable = false,
                        hasDefault = false,
                        required = true,
                    ),
                ),
                returnType = ResourceReturnType.TextType,
            ),
        ),
        prompts = emptyList(),
    )

    assertTrue(generated.contains("addResourceTemplate("), generated)
    assertTrue(generated.contains("uriTemplate = \"audio://tracks/{id}/metadata\""), generated)
    assertTrue(generated.contains("val variables = matchUriTemplate("), generated)
    assertTrue(generated.contains("val id = variables?.get(\"id\")"), generated)
    assertTrue(generated.contains("id = id"), generated)
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates URI template resource registration"
```

Expected: FAIL because dynamic resource generation is not implemented.

- [ ] **Step 3: Generate `addResourceTemplate` registration**

For `ResourceLocation.Template`, generate:

```kotlin
addResourceTemplate(
    uriTemplate = "audio://tracks/{id}/metadata",
    name = "track_metadata",
    description = "Track metadata.",
    mimeType = "application/json",
) { request ->
    try {
        val variables = matchUriTemplate("audio://tracks/{id}/metadata", request.params.uri)
        val id = variables?.get("id")
        if (id == null) {
            return@addResourceTemplate resourceErrorResult(request.params.uri, "Missing required argument: id")
        }
        val result = com.example.resources.trackMetadata(id = id)
        return@addResourceTemplate ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    uri = request.params.uri,
                    mimeType = "application/json",
                    text = result,
                ),
            ),
        )
    } catch (exception: Exception) {
        return@addResourceTemplate resourceErrorResult(request.params.uri, exception.message ?: "Resource failed")
    }
}
```

If SDK 0.12.0 names this API differently, update the generated function name and test expected strings to the exact public method name observed during Task 1 compile verification.

- [ ] **Step 4: Generate `matchUriTemplate` helper when template resources exist**

Add this generated helper:

```kotlin
private fun matchUriTemplate(template: String, uri: String): Map<String, String>? {
    val names = mutableListOf<String>()
    val pattern = buildString {
        append('^')
        var index = 0
        Regex("\\{([A-Za-z_][A-Za-z0-9_]*)}").findAll(template).forEach { match ->
            append(Regex.escape(template.substring(index, match.range.first)))
            names += match.groupValues[1]
            append("([^/]+)")
            index = match.range.last + 1
        }
        append(Regex.escape(template.substring(index)))
        append('$')
    }.toRegex()
    val match = pattern.matchEntire(uri) ?: return null
    return names.zip(match.groupValues.drop(1)).toMap()
}
```

- [ ] **Step 5: Generate `resourceErrorResult` helper when resources exist**

Add this generated helper:

```kotlin
private fun resourceErrorResult(uri: String, message: String): ReadResourceResult = ReadResourceResult(
    contents = listOf(
        TextResourceContents(
            uri = uri,
            mimeType = "text/plain",
            text = message,
        ),
    ),
)
```

- [ ] **Step 6: Run generator tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: PASS.

- [ ] **Step 7: Commit checkpoint if commits were explicitly authorized**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: generate dynamic MCP resource templates"
```

---

### Task 8: Add primitive parsing and return conversions for resources/prompts

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt`
- Modify: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Add failing tests for prompt numeric parsing and native returns**

Add this test to `ToolModelsTest`:

```kotlin
@Test
fun `generates prompt primitive parsing and native result return`() {
    val generated = ToolCodeGenerator.render(
        tools = emptyList(),
        resources = emptyList(),
        prompts = listOf(
            PromptFunction(
                packageName = "com.example.prompts",
                functionName = "ranked",
                promptName = "ranked",
                description = "Ranked prompt.",
                isSuspend = false,
                parameters = listOf(
                    ToolParameter("limit", "limit", "Limit", ParameterType.IntType, nullable = false, hasDefault = false, required = true),
                    ToolParameter("verbose", "verbose", "Verbose", ParameterType.BooleanType, nullable = true, hasDefault = false, required = false),
                ),
                returnType = PromptReturnType.GetPromptResultType,
            ),
        ),
    )

    assertTrue(generated.contains("val limit = arguments?.get(\"limit\")?.toIntOrNull()"), generated)
    assertTrue(generated.contains("val verbose = arguments?.get(\"verbose\")?.toBooleanStrictOrNull()"), generated)
    assertTrue(generated.contains("return@addPrompt result"), generated)
}
```

- [ ] **Step 2: Add failing tests for native resource returns**

Add this test to `ToolModelsTest`:

```kotlin
@Test
fun `generates native resource content returns`() {
    val generated = ToolCodeGenerator.render(
        tools = emptyList(),
        resources = listOf(
            ResourceFunction(
                packageName = "com.example.resources",
                functionName = "blob",
                resourceName = "blob",
                description = "Blob.",
                location = ResourceLocation.Static("file:///blob"),
                mimeType = "application/octet-stream",
                isSuspend = false,
                parameters = emptyList(),
                returnType = ResourceReturnType.BlobResourceContentsType,
            ),
        ),
        prompts = emptyList(),
    )

    assertTrue(generated.contains("return@addResource ReadResourceResult("), generated)
    assertTrue(generated.contains("contents = listOf(result)"), generated)
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: FAIL because parsing and native return conversion are incomplete.

- [ ] **Step 4: Add generated prompt string parsing helpers**

Generate parsing expressions:

```kotlin
private fun ParameterType.promptAccessor(argumentExpression: String): String = when (this) {
    ParameterType.StringType -> argumentExpression
    ParameterType.IntType -> "$argumentExpression?.toIntOrNull()"
    ParameterType.LongType -> "$argumentExpression?.toLongOrNull()"
    ParameterType.DoubleType -> "$argumentExpression?.toDoubleOrNull()"
    ParameterType.BooleanType -> "$argumentExpression?.toBooleanStrictOrNull()"
}
```

Use this shape in generated prompt/resource-template handlers:

```kotlin
val limitPresent = arguments?.containsKey("limit") == true
val limit = arguments?.get("limit")?.toIntOrNull()
if (limitPresent && limit == null) {
    return@addPrompt promptErrorResult("Invalid argument: limit")
}
if (limit == null) {
    return@addPrompt promptErrorResult("Missing required argument: limit")
}
```

- [ ] **Step 5: Add resource return conversion cases**

Generate these conversions:

```kotlin
// String
ReadResourceResult(contents = listOf(TextResourceContents(uri = request.params.uri, mimeType = mimeType, text = result)))

// TextResourceContents or BlobResourceContents
ReadResourceResult(contents = listOf(result))

// ReadResourceResult
result
```

- [ ] **Step 6: Add prompt return conversion cases**

Generate these conversions:

```kotlin
// String
GetPromptResult(description = prompt.description, messages = listOf(PromptMessage(role = Role.USER, content = TextContent(result))))

// PromptMessage
GetPromptResult(description = prompt.description, messages = listOf(result))

// List<PromptMessage>
GetPromptResult(description = prompt.description, messages = result)

// GetPromptResult
result
```

- [ ] **Step 7: Run generator tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: PASS.

- [ ] **Step 8: Commit checkpoint if commits were explicitly authorized**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: convert MCP resource and prompt results"
```

---

### Task 9: Wire generated declarations into the application

**Files:**
- Modify: `essential-mcp/src/commonMain/kotlin/McpTools.kt`
- Modify: `essential-mcp/src/commonMain/kotlin/Server.kt`
- Test: `essential-mcp/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt`

- [ ] **Step 1: Add compile references to generated aggregate functions**

In `GeneratedMcpToolsCompileTest.kt`, add imports:

```kotlin
import io.github.qingshu.mcptool.generated.registerGeneratedMcpDeclarations
import io.github.qingshu.mcptool.generated.registerGeneratedMcpPrompts
import io.github.qingshu.mcptool.generated.registerGeneratedMcpResources
```

Add test:

```kotlin
@Test
fun generatedDeclarationRegistriesCompile() {
    val server = createServer()
    server.registerGeneratedMcpTools()
    server.registerGeneratedMcpResources()
    server.registerGeneratedMcpPrompts()
    server.registerGeneratedMcpDeclarations()
}
```

Use the existing server construction helper in that file. If no helper exists, create one in the test file using the same `Server(...)` construction already used by the existing compile test.

- [ ] **Step 2: Run compile test and verify it fails**

Run:

```bash
./gradlew :essential-mcp:compileTestKotlinJvm
```

Expected: FAIL until generated resource/prompt aggregate functions are emitted into the generated source set.

- [ ] **Step 3: Update `McpTools.kt`**

Change `McpTools.kt` to:

```kotlin
package io.github.qingshu.essentialmcp

import io.github.qingshu.mcptool.generated.registerGeneratedMcpDeclarations
import io.modelcontextprotocol.kotlin.sdk.server.Server

fun Server.mcpToolRegistry() {
    registerGeneratedMcpDeclarations()
}
```

- [ ] **Step 4: Enable resource and prompt capabilities if needed**

Inspect `essential-mcp/src/commonMain/kotlin/Server.kt`. If SDK 0.12.0 requires explicit capabilities, configure:

```kotlin
ServerCapabilities(
    tools = ServerCapabilities.Tools(listChanged = true),
    resources = ServerCapabilities.Resources(subscribe = false, listChanged = true),
    prompts = ServerCapabilities.Prompts(listChanged = true),
    logging = ServerCapabilities.Logging,
)
```

If SDK 0.12.0 uses different property names, use the exact names from compile errors and keep tools/logging behavior unchanged.

- [ ] **Step 5: Run compile test and metadata compile**

Run:

```bash
./gradlew :essential-mcp:compileTestKotlinJvm :essential-mcp:compileKotlinMetadata
```

Expected: PASS.

- [ ] **Step 6: Commit checkpoint if commits were explicitly authorized**

```bash
git add essential-mcp/src/commonMain/kotlin/McpTools.kt essential-mcp/src/commonMain/kotlin/Server.kt essential-mcp/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt
git commit -m "feat: register generated MCP declarations"
```

---

### Task 10: Add sample annotated resource and prompt compile coverage

**Files:**
- Create: `essential-mcp/src/commonMain/kotlin/mcptool/AudioResources.kt`
- Create: `essential-mcp/src/commonMain/kotlin/mcptool/AudioPrompts.kt`
- Test: generated compile path through `essential-mcp` metadata compilation

- [ ] **Step 1: Add a static and dynamic sample resource**

Create `essential-mcp/src/commonMain/kotlin/mcptool/AudioResources.kt`:

```kotlin
package io.github.qingshu.essentialmcp.mcptool

import io.github.qingshu.mcptool.annotations.McpResource

@McpResource(
    uri = "audio://server/info",
    name = "audio_server_info",
    description = "Information about the MCP audio tools server.",
    mimeType = "text/plain",
)
fun audioServerInfo(): String = "MCP audio tools server"

@McpResource(
    uriTemplate = "audio://files/{path}/summary",
    name = "audio_file_summary",
    description = "Summary placeholder for an audio file path.",
    mimeType = "text/plain",
)
fun audioFileSummary(path: String): String = "Audio file: $path"
```

- [ ] **Step 2: Add a sample prompt**

Create `essential-mcp/src/commonMain/kotlin/mcptool/AudioPrompts.kt`:

```kotlin
package io.github.qingshu.essentialmcp.mcptool

import io.github.qingshu.mcptool.annotations.McpPrompt
import io.github.qingshu.mcptool.annotations.PromptParam

@McpPrompt(
    name = "summarize_audio",
    description = "Create a prompt for summarizing an audio file.",
)
fun summarizeAudioPrompt(
    @PromptParam(description = "Path to the audio file.", name = "audio_path")
    audioPath: String,
): String = "Summarize the audio file at: $audioPath"
```

- [ ] **Step 3: Run metadata compilation and verify generated code compiles**

Run:

```bash
./gradlew :essential-mcp:compileKotlinMetadata
```

Expected: PASS and generated code includes resource and prompt registrations for the sample functions.

- [ ] **Step 4: Commit checkpoint if commits were explicitly authorized**

```bash
git add essential-mcp/src/commonMain/kotlin/mcptool/AudioResources.kt essential-mcp/src/commonMain/kotlin/mcptool/AudioPrompts.kt
git commit -m "feat: add MCP resource and prompt declarations"
```

---

### Task 11: Full verification and formatting

**Files:**
- Modify only files changed by `spotlessApply`.

- [ ] **Step 1: Run Spotless formatting**

Run:

```bash
./gradlew spotlessApply
```

Expected: PASS. Review changed files and keep only formatting changes related to this work.

- [ ] **Step 2: Run KSP tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test
```

Expected: PASS.

- [ ] **Step 3: Run MCP audio metadata compile**

Run:

```bash
./gradlew :essential-mcp:compileKotlinMetadata :essential-mcp:compileTestKotlinMetadata
```

Expected: PASS.

- [ ] **Step 4: Run full test suite if time allows**

Run:

```bash
./gradlew test
```

Expected: PASS. If platform-specific tests fail for missing external binaries or environment differences, capture the exact failing task and error before deciding whether it is unrelated.

- [ ] **Step 5: Inspect generated output**

Run:

```bash
grep -R "registerGeneratedMcpDeclarations\|addResource\|addResourceTemplate\|addPrompt" -n essential-mcp/build/generated/ksp/metadata/commonMain/kotlin
```

Expected: output includes generated aggregate, resource, template resource, and prompt registrations.

- [ ] **Step 6: Commit checkpoint if commits were explicitly authorized**

```bash
git add gradle/libs.versions.toml mcp-tool-annotations/src/commonMain/kotlin/McpResource.kt mcp-tool-annotations/src/commonMain/kotlin/McpPrompt.kt mcp-tool-annotations/src/commonMain/kotlin/PromptParam.kt mcp-tool-ksp/src/main/kotlin/ToolModels.kt mcp-tool-ksp/src/main/kotlin/ToolValidator.kt mcp-tool-ksp/src/main/kotlin/ResourcePromptValidator.kt mcp-tool-ksp/src/main/kotlin/McpToolProcessor.kt mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt mcp-tool-ksp/src/test/kotlin/ToolValidatorTest.kt mcp-tool-ksp/src/test/kotlin/ResourcePromptValidatorTest.kt essential-mcp/src/commonMain/kotlin/McpTools.kt essential-mcp/src/commonMain/kotlin/Server.kt essential-mcp/src/commonMain/kotlin/mcptool/AudioResources.kt essential-mcp/src/commonMain/kotlin/mcptool/AudioPrompts.kt essential-mcp/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt
git commit -m "feat: generate MCP resources and prompts"
```

---

## Self-Review Notes

- Spec coverage: the plan covers SDK 0.12.0 upgrade, annotations, static resources, dynamic URI-template resources, prompts with arguments, generated aggregate functions, validation, runtime conversion, app wiring, and tests.
- Ambiguity resolved: dynamic resources target SDK-native `addResourceTemplate`; compile verification in Task 1 and Task 7 forces exact API alignment if SDK 0.12.0 method names differ.
- Commit safety: commit commands are included as checkpoints for authorized commit sessions only; execution must not commit unless the user explicitly asks.
- Existing behavior: tool generation tests remain in place and must pass after generator signature changes.
