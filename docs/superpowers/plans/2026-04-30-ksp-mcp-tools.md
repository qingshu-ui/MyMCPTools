# KSP Annotation-Oriented MCP Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a KSP-based annotation API that lets MCP tools be defined as documented Kotlin functions in `commonMain` while generated adapters handle MCP SDK registration, schemas, argument extraction, result wrapping, and exception conversion.

**Architecture:** Add a multiplatform `mcp-tool-annotations` module for source-retained annotations and a JVM `mcp-tool-ksp` module for the processor. The app module applies KSP to annotated common tool functions and delegates `Server.mcpToolRegistry()` to generated registration code that calls MCP SDK `Server.addTool(...)` directly.

**Tech Stack:** Kotlin 2.3.10, Kotlin Multiplatform, KSP, kotlinx.serialization JSON, MCP Kotlin SDK 0.9.0, Gradle Kotlin DSL, kotlin-test, Spotless/ktlint.

---

## File Structure

Create and modify these files:

- Modify `gradle/libs.versions.toml`
  - Add KSP plugin and KotlinPoet dependencies.
  - Add kotlin-compile-testing dependencies if compatible with Gradle/Kotlin version; otherwise use processor unit tests plus generated source integration tests.

- Modify `settings.gradle.kts`
  - Include `mcp-tool-annotations` and `mcp-tool-ksp`.

- Modify root `build.gradle.kts`
  - Register the KSP plugin alias with `apply false`.
  - Ensure Spotless covers the new modules through existing `allprojects` config.

- Create `mcp-tool-annotations/build.gradle.kts`
  - Multiplatform module with common annotations.

- Create `mcp-tool-annotations/src/commonMain/kotlin/io/github/qingshu/mcptool/annotations/McpTool.kt`
  - Defines `@McpTool`.

- Create `mcp-tool-annotations/src/commonMain/kotlin/io/github/qingshu/mcptool/annotations/ToolParam.kt`
  - Defines `@ToolParam` and a tri-state `Required` enum.

- Create `mcp-tool-ksp/build.gradle.kts`
  - JVM module for the KSP processor.
  - Depends on KSP API, KotlinPoet, KotlinPoet KSP interop, annotations, MCP SDK server, and test dependencies.

- Create `mcp-tool-ksp/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
  - Registers the provider class.

- Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/McpToolProcessorProvider.kt`
  - Provides the KSP processor.

- Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/McpToolProcessor.kt`
  - Coordinates symbol discovery, validation, model building, duplicate detection, and generation.

- Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolModels.kt`
  - Holds internal immutable models such as `ToolFunction`, `ToolParameter`, `ParameterType`, and `ReturnType`.

- Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolValidator.kt`
  - Validates annotated functions and emits actionable KSP diagnostics.

- Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`
  - Generates `GeneratedMcpTools.kt` using KotlinPoet.

- Create `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolValidatorTest.kt`
  - Tests pure validation/model helpers without Gradle compilation.

- Create `mcp-audio-tools/src/commonMain/kotlin/mcptool/GeneratedToolDefinitions.kt`
  - Contains annotated tool implementation functions migrated from existing manual registration functions.

- Modify `mcp-audio-tools/src/commonMain/kotlin/McpTools.kt`
  - Imports generated `registerGeneratedMcpTools` and delegates to it.

- Modify `mcp-audio-tools/build.gradle.kts`
  - Add annotations dependency.
  - Apply KSP plugin.
  - Add KSP processor dependency.
  - Wire generated KSP common metadata sources into common compilation if the plugin does not do so automatically.

- Modify or remove old manual registration files:
  - `mcp-audio-tools/src/commonMain/kotlin/mcptool/TranscodeWavToMp3.kt`
  - `mcp-audio-tools/src/commonMain/kotlin/mcptool/SubtitleToLrc.kt`
  - `mcp-audio-tools/src/commonMain/kotlin/mcptool/ExecuteCommand.kt`
  - Keep reusable private helpers where helpful, but remove `fun Server.*()` manual wrappers.

- Create `mcp-audio-tools/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt`
  - Smoke test that `McpServer { registerGeneratedMcpTools() }` compiles and registers without requiring external tools.

---

### Task 1: Add KSP and Codegen Dependencies to Version Catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add version catalog entries**

Modify `gradle/libs.versions.toml` so the relevant sections include these entries:

```toml
[versions]
clikt = "5.1.0"
kotlin = "2.3.10"
ksp = "2.3.10-2.0.0"
kotlinpoet = "2.2.0"
spotless = "8.3.0"
kotlinxCoroutines = "1.10.2"
kotlinxSerialization = "1.9.0"
kotlinxIo = "0.9.0"
mcp = "0.9.0"
nexusPublish = "2.0.0"
dokka = "2.1.0"

[libraries]
clikt = { module = "com.github.ajalt.clikt:clikt", version.ref = "clikt" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-serializationJson = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-io = { module = "org.jetbrains.kotlinx:kotlinx-io-core", version.ref = "kotlinxIo" }
ksp-api = { module = "com.google.devtools.ksp:symbol-processing-api", version.ref = "ksp" }
kotlinpoet = { module = "com.squareup:kotlinpoet", version.ref = "kotlinpoet" }
kotlinpoet-ksp = { module = "com.squareup:kotlinpoet-ksp", version.ref = "kotlinpoet" }

mcp = { module = "io.modelcontextprotocol:kotlin-sdk", version.ref = "mcp" }
mcp-client = { module = "io.modelcontextprotocol:kotlin-sdk-client", version.ref = "mcp" }
mcp-server = { module = "io.modelcontextprotocol:kotlin-sdk-server", version.ref = "mcp" }

[plugins]
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinxSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
nexusPublish = { id = "io.github.gradle-nexus.publish-plugin", version.ref = "nexusPublish" }
mavenPublish = { id = "maven-publish" }
signing = { id = "signing" }
dokka = { id = "org.jetbrains.dokka", version.ref = "dokka" }
```

Important: preserve existing entries not shown if the file has changed. If Gradle cannot resolve `2.3.10-2.0.0`, check the KSP release matching Kotlin 2.3.10 and update only the `ksp` version.

- [ ] **Step 2: Run version catalog syntax check**

Run:

```bash
./gradlew help
```

Expected: Gradle configures successfully. If it fails on KSP resolution, update `ksp` to the published KSP version for Kotlin `2.3.10` and rerun.

- [ ] **Step 3: Commit dependency catalog change**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: add ksp codegen dependencies"
```

---

### Task 2: Register New Modules and Root Plugin Alias

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Update settings includes**

Modify the end of `settings.gradle.kts` to include the new modules:

```kotlin
rootProject.name = "MyMCPTools"
include("mcp-audio-tools")
include("process")
include("mcp-tool-annotations")
include("mcp-tool-ksp")
```

- [ ] **Step 2: Update root plugins block**

Modify the root `build.gradle.kts` plugins block to include KSP with `apply false`:

```kotlin
plugins {
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.nexusPublish)
    alias(libs.plugins.dokka) apply false
}
```

- [ ] **Step 3: Run Gradle help**

Run:

```bash
./gradlew help
```

Expected: Gradle configures successfully or fails only because the included module directories do not exist yet. If it fails because directories are missing, continue to Task 3 before rerunning.

- [ ] **Step 4: Commit module registration**

```bash
git add settings.gradle.kts build.gradle.kts
git commit -m "chore: register mcp tool codegen modules"
```

---

### Task 3: Create the Multiplatform Annotation Module

**Files:**
- Create: `mcp-tool-annotations/build.gradle.kts`
- Create: `mcp-tool-annotations/src/commonMain/kotlin/io/github/qingshu/mcptool/annotations/McpTool.kt`
- Create: `mcp-tool-annotations/src/commonMain/kotlin/io/github/qingshu/mcptool/annotations/ToolParam.kt`

- [ ] **Step 1: Create annotation module build file**

Create `mcp-tool-annotations/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "io.github.qingshu-ui"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
    jvm()
    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
```

- [ ] **Step 2: Create `@McpTool` annotation**

Create `mcp-tool-annotations/src/commonMain/kotlin/io/github/qingshu/mcptool/annotations/McpTool.kt`:

```kotlin
package io.github.qingshu.mcptool.annotations

/**
 * Marks a top-level function as an MCP tool definition.
 *
 * The KSP processor generates MCP SDK registration code for annotated functions.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class McpTool(
    public val name: String,
    public val description: String,
)
```

- [ ] **Step 3: Create `@ToolParam` annotation**

Create `mcp-tool-annotations/src/commonMain/kotlin/io/github/qingshu/mcptool/annotations/ToolParam.kt`:

```kotlin
package io.github.qingshu.mcptool.annotations

/**
 * Tri-state requiredness for tool parameters.
 *
 * UNSPECIFIED lets the processor infer requiredness from Kotlin nullability and defaults.
 */
public enum class Required {
    UNSPECIFIED,
    TRUE,
    FALSE,
}

/**
 * Documents a parameter exposed in a generated MCP input schema.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class ToolParam(
    public val description: String,
    public val required: Required = Required.UNSPECIFIED,
)
```

- [ ] **Step 4: Build annotation module**

Run:

```bash
./gradlew :mcp-tool-annotations:build
```

Expected: PASS.

- [ ] **Step 5: Commit annotation module**

```bash
git add mcp-tool-annotations
git commit -m "feat: add mcp tool annotations"
```

---

### Task 4: Create KSP Processor Module Skeleton

**Files:**
- Create: `mcp-tool-ksp/build.gradle.kts`
- Create: `mcp-tool-ksp/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
- Create: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/McpToolProcessorProvider.kt`
- Create: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/McpToolProcessor.kt`

- [ ] **Step 1: Create KSP module build file**

Create `mcp-tool-ksp/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "io.github.qingshu-ui"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(projects.mcpToolAnnotations)
    implementation(libs.mcp.server)

    testImplementation(libs.kotlin.test)
}
```

- [ ] **Step 2: Register processor provider service**

Create `mcp-tool-ksp/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` with exactly this content:

```text
io.github.qingshu.mcptool.ksp.McpToolProcessorProvider
```

- [ ] **Step 3: Create provider class**

Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/McpToolProcessorProvider.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

public class McpToolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = McpToolProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
    )
}

internal data class ProcessorContext(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
)
```

- [ ] **Step 4: Create no-op processor**

Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/McpToolProcessor.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

internal class McpToolProcessor(
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
) : SymbolProcessor {
    private val context = ProcessorContext(codeGenerator, logger)
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true
        context.logger.info("MCP tool processor initialized")
        return emptyList()
    }
}
```

- [ ] **Step 5: Build processor module**

Run:

```bash
./gradlew :mcp-tool-ksp:build
```

Expected: PASS.

- [ ] **Step 6: Commit processor skeleton**

```bash
git add mcp-tool-ksp
git commit -m "feat: add mcp tool ksp processor skeleton"
```

---

### Task 5: Add Internal Processor Models and Pure Type Mapping Tests

**Files:**
- Create: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolModels.kt`
- Create: `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`

- [ ] **Step 1: Write failing tests for supported type mapping**

Create `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolModelsTest {
    @Test
    fun `maps supported Kotlin types to JSON schema types`() {
        assertEquals(ParameterType.StringType, ParameterType.fromQualifiedName("kotlin.String"))
        assertEquals(ParameterType.IntType, ParameterType.fromQualifiedName("kotlin.Int"))
        assertEquals(ParameterType.LongType, ParameterType.fromQualifiedName("kotlin.Long"))
        assertEquals(ParameterType.DoubleType, ParameterType.fromQualifiedName("kotlin.Double"))
        assertEquals(ParameterType.BooleanType, ParameterType.fromQualifiedName("kotlin.Boolean"))
    }

    @Test
    fun `returns null for unsupported Kotlin types`() {
        assertNull(ParameterType.fromQualifiedName("kotlin.collections.List"))
        assertNull(ParameterType.fromQualifiedName("com.example.Custom"))
    }

    @Test
    fun `maps parameter types to JSON schema names`() {
        assertEquals("string", ParameterType.StringType.jsonSchemaType)
        assertEquals("integer", ParameterType.IntType.jsonSchemaType)
        assertEquals("integer", ParameterType.LongType.jsonSchemaType)
        assertEquals("number", ParameterType.DoubleType.jsonSchemaType)
        assertEquals("boolean", ParameterType.BooleanType.jsonSchemaType)
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: FAIL because `ParameterType` does not exist.

- [ ] **Step 3: Implement models**

Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolModels.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import com.squareup.kotlinpoet.ClassName

data class ToolFunction(
    val packageName: String,
    val functionName: String,
    val toolName: String,
    val description: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val returnType: ToolReturnType,
)

data class ToolParameter(
    val name: String,
    val description: String,
    val type: ParameterType,
    val nullable: Boolean,
    val hasDefault: Boolean,
    val required: Boolean,
)

sealed class ParameterType(
    val kotlinType: ClassName,
    val jsonSchemaType: String,
) {
    data object StringType : ParameterType(ClassName("kotlin", "String"), "string")
    data object IntType : ParameterType(ClassName("kotlin", "Int"), "integer")
    data object LongType : ParameterType(ClassName("kotlin", "Long"), "integer")
    data object DoubleType : ParameterType(ClassName("kotlin", "Double"), "number")
    data object BooleanType : ParameterType(ClassName("kotlin", "Boolean"), "boolean")

    companion object {
        fun fromQualifiedName(qualifiedName: String): ParameterType? = when (qualifiedName) {
            "kotlin.String" -> StringType
            "kotlin.Int" -> IntType
            "kotlin.Long" -> LongType
            "kotlin.Double" -> DoubleType
            "kotlin.Boolean" -> BooleanType
            else -> null
        }
    }
}

sealed class ToolReturnType {
    data object UnitType : ToolReturnType()
    data object TextType : ToolReturnType()
    data object PrimitiveType : ToolReturnType()
    data object CallToolResultType : ToolReturnType()
}
```

- [ ] **Step 4: Run tests and verify pass**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: PASS.

- [ ] **Step 5: Commit models**

```bash
git add mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolModels.kt mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt
git commit -m "feat: model generated mcp tool metadata"
```

---

### Task 6: Implement Pure Requiredness Validation Tests

**Files:**
- Create: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolValidator.kt`
- Create: `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolValidatorTest.kt`

- [ ] **Step 1: Write failing requiredness tests**

Create `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolValidatorTest.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import io.github.qingshu.mcptool.annotations.Required
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ToolValidatorTest {
    @Test
    fun `non-null parameter without default is required by inference`() {
        val required = inferRequiredness(nullable = false, hasDefault = false, explicit = Required.UNSPECIFIED)
        assertEquals(true, required)
    }

    @Test
    fun `nullable parameter is optional by inference`() {
        val required = inferRequiredness(nullable = true, hasDefault = false, explicit = Required.UNSPECIFIED)
        assertEquals(false, required)
    }

    @Test
    fun `default parameter is optional by inference`() {
        val required = inferRequiredness(nullable = false, hasDefault = true, explicit = Required.UNSPECIFIED)
        assertEquals(false, required)
    }

    @Test
    fun `explicit false can mark non-null parameter optional`() {
        val required = inferRequiredness(nullable = false, hasDefault = true, explicit = Required.FALSE)
        assertEquals(false, required)
    }

    @Test
    fun `explicit true on nullable parameter fails`() {
        val error = assertFailsWith<IllegalArgumentException> {
            inferRequiredness(nullable = true, hasDefault = false, explicit = Required.TRUE)
        }
        assertEquals("Parameter cannot be required when its Kotlin type is nullable or has a default value.", error.message)
    }

    @Test
    fun `explicit true on default parameter fails`() {
        val error = assertFailsWith<IllegalArgumentException> {
            inferRequiredness(nullable = false, hasDefault = true, explicit = Required.TRUE)
        }
        assertEquals("Parameter cannot be required when its Kotlin type is nullable or has a default value.", error.message)
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolValidatorTest"
```

Expected: FAIL because `inferRequiredness` does not exist.

- [ ] **Step 3: Implement requiredness helper**

Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolValidator.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import io.github.qingshu.mcptool.annotations.Required

internal fun inferRequiredness(
    nullable: Boolean,
    hasDefault: Boolean,
    explicit: Required,
): Boolean = when (explicit) {
    Required.UNSPECIFIED -> !nullable && !hasDefault
    Required.FALSE -> false
    Required.TRUE -> {
        require(!nullable && !hasDefault) {
            "Parameter cannot be required when its Kotlin type is nullable or has a default value."
        }
        true
    }
}
```

- [ ] **Step 4: Run tests and verify pass**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolValidatorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit validation helper**

```bash
git add mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolValidator.kt mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolValidatorTest.kt
git commit -m "feat: validate mcp tool parameter requiredness"
```

---

### Task 7: Implement KSP Symbol Validation and Model Extraction

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolValidator.kt`
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/McpToolProcessor.kt`

- [ ] **Step 1: Add KSP validation/model extraction code**

Append to `ToolValidator.kt` after `inferRequiredness`:

```kotlin
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import io.github.qingshu.mcptool.annotations.McpTool
import io.github.qingshu.mcptool.annotations.ToolParam
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult

private const val MCP_TOOL_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpTool"
private const val TOOL_PARAM_ANNOTATION = "io.github.qingshu.mcptool.annotations.ToolParam"
private const val CALL_TOOL_RESULT = "io.modelcontextprotocol.kotlin.sdk.types.CallToolResult"

internal fun KSFunctionDeclaration.toToolFunctionOrNull(logger: KSPLogger): ToolFunction? {
    val toolAnnotation = annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == MCP_TOOL_ANNOTATION }
        ?: return null

    if (parentDeclaration != null) {
        logger.error("@McpTool is only supported on top-level functions in v1.", this)
        return null
    }

    val toolName = toolAnnotation.argumentValue<String>("name").orEmpty()
    val description = toolAnnotation.argumentValue<String>("description").orEmpty()

    if (toolName.isBlank()) {
        logger.error("@McpTool name must not be blank.", this)
        return null
    }
    if (description.isBlank()) {
        logger.error("@McpTool description must not be blank.", this)
        return null
    }

    val parameters = parameters.mapNotNull { parameter -> parameter.toToolParameterOrNull(logger) }
    if (parameters.size != this.parameters.size) return null

    val returnType = resolveReturnType(logger) ?: return null

    return ToolFunction(
        packageName = packageName.asString(),
        functionName = simpleName.asString(),
        toolName = toolName,
        description = description,
        isSuspend = modifiers.contains(Modifier.SUSPEND),
        parameters = parameters,
        returnType = returnType,
    )
}

private fun KSValueParameter.toToolParameterOrNull(logger: KSPLogger): ToolParameter? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpTool parameters must have stable names.", this)
        return null
    }

    val annotation = annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == TOOL_PARAM_ANNOTATION }
    if (annotation == null) {
        logger.error("Parameter '$parameterName' must be annotated with @ToolParam.", this)
        return null
    }

    val description = annotation.argumentValue<String>("description").orEmpty()
    if (description.isBlank()) {
        logger.error("@ToolParam description for '$parameterName' must not be blank.", this)
        return null
    }

    val resolvedType = type.resolve()
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()
    val parameterType = ParameterType.fromQualifiedName(qualifiedType)
    if (parameterType == null) {
        logger.error("Unsupported @ToolParam type '$qualifiedType' for '$parameterName'. Supported types: String, Int, Long, Double, Boolean.", this)
        return null
    }

    val explicitRequired = annotation.argumentValue<io.github.qingshu.mcptool.annotations.Required>("required")
        ?: io.github.qingshu.mcptool.annotations.Required.UNSPECIFIED

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

    return ToolParameter(
        name = parameterName,
        description = description,
        type = parameterType,
        nullable = resolvedType.isMarkedNullable,
        hasDefault = hasDefault,
        required = required,
    )
}

private fun KSFunctionDeclaration.resolveReturnType(logger: KSPLogger): ToolReturnType? {
    val resolved = returnType?.resolve()
    val qualifiedName = resolved?.declaration?.qualifiedName?.asString() ?: "kotlin.Unit"
    return when (qualifiedName) {
        "kotlin.Unit" -> ToolReturnType.UnitType
        "kotlin.String" -> ToolReturnType.TextType
        "kotlin.Int", "kotlin.Long", "kotlin.Double", "kotlin.Boolean" -> ToolReturnType.PrimitiveType
        CALL_TOOL_RESULT -> ToolReturnType.CallToolResultType
        else -> {
            logger.error("Unsupported @McpTool return type '$qualifiedName'. Supported returns: Unit, String, Int, Long, Double, Boolean, CallToolResult.", this)
            null
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> com.google.devtools.ksp.symbol.KSAnnotation.argumentValue(name: String): T? =
    arguments.firstOrNull { it.name?.asString() == name }?.value as? T
```

If Kotlin reports imports cannot appear after declarations, move all imports to the top of `ToolValidator.kt` and keep exactly one `package` declaration.

- [ ] **Step 2: Update processor to discover annotated functions and duplicate names**

Replace `McpToolProcessor.kt` with:

```kotlin
package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

private const val MCP_TOOL_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpTool"

internal class McpToolProcessor(
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
) : SymbolProcessor {
    private val context = ProcessorContext(codeGenerator, logger)
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val tools = resolver
            .getSymbolsWithAnnotation(MCP_TOOL_ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .mapNotNull { it.toToolFunctionOrNull(context.logger) }
            .toList()

        val duplicateNames = tools.groupBy { it.toolName }.filterValues { it.size > 1 }.keys
        duplicateNames.forEach { name ->
            context.logger.error("Duplicate MCP tool name '$name'. Tool names must be unique.")
        }

        if (tools.isNotEmpty() && duplicateNames.isEmpty()) {
            ToolCodeGenerator(context).generate(tools)
        }

        return emptyList()
    }
}
```

This references `ToolCodeGenerator`, which Task 8 will create. If compiling before Task 8, temporarily comment the generation call and uncomment it in Task 8.

- [ ] **Step 3: Run processor build and observe expected failure**

Run:

```bash
./gradlew :mcp-tool-ksp:compileKotlin
```

Expected before Task 8: FAIL only because `ToolCodeGenerator` does not exist. If there are validation/import errors, fix them before continuing.

- [ ] **Step 4: Commit symbol validation**

If `ToolCodeGenerator` is not created yet, do not commit a broken compile state. Either create a tiny temporary stub:

```kotlin
package io.github.qingshu.mcptool.ksp

internal class ToolCodeGenerator(private val context: ProcessorContext) {
    fun generate(tools: List<ToolFunction>) = Unit
}
```

or defer this commit until Task 8. Prefer deferring if following strict green commits.

---

### Task 8: Generate MCP Registration Code

**Files:**
- Create: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/McpToolProcessor.kt` if generation call was deferred

- [ ] **Step 1: Implement code generator**

Create `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`:

```kotlin
package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private val serverClass = ClassName("io.modelcontextprotocol.kotlin.sdk.server", "Server")
private val callToolResultClass = ClassName("io.modelcontextprotocol.kotlin.sdk.types", "CallToolResult")
private val textContentClass = ClassName("io.modelcontextprotocol.kotlin.sdk.types", "TextContent")
private val toolSchemaClass = ClassName("io.modelcontextprotocol.kotlin.sdk.types", "ToolSchema")
private val jsonObjectClass = ClassName("kotlinx.serialization.json", "JsonObject")
private val jsonPrimitiveClass = ClassName("kotlinx.serialization.json", "JsonPrimitive")
private val buildJsonObjectFun = ClassName("kotlinx.serialization.json", "buildJsonObject")
private val putJsonObjectFun = ClassName("kotlinx.serialization.json", "putJsonObject")
private val putFun = ClassName("kotlinx.serialization.json", "put")
private val jsonPrimitiveProperty = ClassName("kotlinx.serialization.json", "jsonPrimitive")
private val contentProperty = ClassName("kotlinx.serialization.json", "content")
private val booleanOrNullProperty = ClassName("kotlinx.serialization.json", "booleanOrNull")
private val doubleOrNullProperty = ClassName("kotlinx.serialization.json", "doubleOrNull")
private val intOrNullProperty = ClassName("kotlinx.serialization.json", "intOrNull")
private val longOrNullProperty = ClassName("kotlinx.serialization.json", "longOrNull")

internal class ToolCodeGenerator(
    private val context: ProcessorContext,
) {
    fun generate(tools: List<ToolFunction>) {
        val file = FileSpec.builder("io.github.qingshu.mcpaudiotools.generated", "GeneratedMcpTools")
            .addFunction(aggregateFunction(tools))
            .apply {
                tools.forEach { addFunction(registrationFunction(it)) }
            }
            .build()

        file.writeTo(
            codeGenerator = context.codeGenerator,
            dependencies = Dependencies(aggregating = true),
        )
    }

    private fun aggregateFunction(tools: List<ToolFunction>): FunSpec = FunSpec.builder("registerGeneratedMcpTools")
        .receiver(serverClass)
        .apply {
            tools.sortedBy { it.toolName }.forEach { tool ->
                addStatement("%N()", registrationFunctionName(tool))
            }
        }
        .build()

    private fun registrationFunction(tool: ToolFunction): FunSpec = FunSpec.builder(registrationFunctionName(tool))
        .receiver(serverClass)
        .addCode(
            CodeBlock.builder()
                .addStatement("addTool(")
                .indent()
                .addStatement("name = %S,", tool.toolName)
                .addStatement("description = %S,", tool.description)
                .addStatement("inputSchema = %T(", toolSchemaClass)
                .indent()
                .add("properties = %T {\n", buildJsonObjectFun)
                .indent()
                .apply {
                    tool.parameters.forEach { parameter ->
                        addStatement("%T(%S) {", putJsonObjectFun, parameter.name)
                        indent()
                        addStatement("%T(%S, %S)", putFun, "type", parameter.type.jsonSchemaType)
                        addStatement("%T(%S, %S)", putFun, "description", parameter.description)
                        unindent()
                        addStatement("}")
                    }
                }
                .unindent()
                .addStatement("},")
                .addStatement("required = %L,", requiredListLiteral(tool))
                .unindent()
                .addStatement("),")
                .unindent()
                .addStatement(") { call ->")
                .indent()
                .addStatement("try {")
                .indent()
                .add(argumentExtraction(tool))
                .add(returnConversion(tool))
                .unindent()
                .addStatement("} catch (e: Exception) {")
                .indent()
                .addStatement("%T(content = listOf(%T(e.message ?: %S)), isError = true)", callToolResultClass, textContentClass, "Tool failed")
                .unindent()
                .addStatement("}")
                .unindent()
                .addStatement("}")
                .build(),
        )
        .build()

    private fun argumentExtraction(tool: ToolFunction): CodeBlock = CodeBlock.builder()
        .apply {
            tool.parameters.forEach { parameter -> add(parameterExtraction(parameter)) }
        }
        .build()

    private fun parameterExtraction(parameter: ToolParameter): CodeBlock {
        val access = "call.params.arguments?.get(%S)?.jsonPrimitive"
        val missing = "Missing required argument: ${parameter.name}"
        return CodeBlock.builder()
            .apply {
                when (parameter.type) {
                    ParameterType.StringType -> {
                        if (parameter.required) {
                            addStatement("val %N = $access?.content ?: error(%S)", parameter.name, parameter.name, missing)
                        } else {
                            addStatement("val %N = $access?.content", parameter.name, parameter.name)
                        }
                    }
                    ParameterType.IntType -> {
                        if (parameter.required) {
                            addStatement("val %N = $access?.intOrNull ?: error(%S)", parameter.name, parameter.name, missing)
                        } else {
                            addStatement("val %N = $access?.intOrNull", parameter.name, parameter.name)
                        }
                    }
                    ParameterType.LongType -> {
                        if (parameter.required) {
                            addStatement("val %N = $access?.longOrNull ?: error(%S)", parameter.name, parameter.name, missing)
                        } else {
                            addStatement("val %N = $access?.longOrNull", parameter.name, parameter.name)
                        }
                    }
                    ParameterType.DoubleType -> {
                        if (parameter.required) {
                            addStatement("val %N = $access?.doubleOrNull ?: error(%S)", parameter.name, parameter.name, missing)
                        } else {
                            addStatement("val %N = $access?.doubleOrNull", parameter.name, parameter.name)
                        }
                    }
                    ParameterType.BooleanType -> {
                        if (parameter.required) {
                            addStatement("val %N = $access?.booleanOrNull ?: error(%S)", parameter.name, parameter.name, missing)
                        } else {
                            addStatement("val %N = $access?.booleanOrNull", parameter.name, parameter.name)
                        }
                    }
                }
            }
            .build()
    }

    private fun returnConversion(tool: ToolFunction): CodeBlock {
        val call = buildString {
            append("%M(")
            append(tool.parameters.joinToString(", ") { it.name })
            append(")")
        }
        val member = com.squareup.kotlinpoet.MemberName(tool.packageName, tool.functionName)
        return CodeBlock.builder()
            .apply {
                when (tool.returnType) {
                    ToolReturnType.CallToolResultType -> addStatement("%L", CodeBlock.of(call, member))
                    ToolReturnType.UnitType -> {
                        addStatement(CodeBlock.of(call, member).toString())
                        addStatement("%T(content = listOf(%T(%S)), isError = false)", callToolResultClass, textContentClass, "[OK]")
                    }
                    ToolReturnType.TextType,
                    ToolReturnType.PrimitiveType,
                    -> {
                        addStatement("val result = %L", CodeBlock.of(call, member))
                        addStatement("%T(content = listOf(%T(result.toString())), isError = false)", callToolResultClass, textContentClass)
                    }
                }
            }
            .build()
    }

    private fun requiredListLiteral(tool: ToolFunction): CodeBlock {
        val required = tool.parameters.filter { it.required }.map { it.name }
        return if (required.isEmpty()) {
            CodeBlock.of("emptyList()")
        } else {
            CodeBlock.of("listOf(%L)", required.joinToString(", ") { "\"$it\"" })
        }
    }

    private fun registrationFunctionName(tool: ToolFunction): String = "register" +
        tool.functionName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } +
        "McpTool"
}
```

Note: after implementation, compile errors may identify unused imports or bad KotlinPoet format strings. Fix the generator so generated source compiles; do not suppress real generator problems.

- [ ] **Step 2: Run processor tests and compile**

Run:

```bash
./gradlew :mcp-tool-ksp:test :mcp-tool-ksp:compileKotlin
```

Expected: PASS.

- [ ] **Step 3: Commit generator**

```bash
git add mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp
git commit -m "feat: generate mcp tool registration adapters"
```

---

### Task 9: Wire KSP into `mcp-audio-tools`

**Files:**
- Modify: `mcp-audio-tools/build.gradle.kts`

- [ ] **Step 1: Apply KSP plugin and dependencies**

Modify `mcp-audio-tools/build.gradle.kts` plugins block:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
}
```

Modify `commonMain.dependencies`:

```kotlin
commonMain.dependencies {
    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.mcp.server)
    implementation(projects.process)
    implementation(projects.mcpToolAnnotations)
}
```

Add dependencies block after `kotlin { ... }`:

```kotlin
dependencies {
    add("kspCommonMainMetadata", projects.mcpToolKsp)
}
```

If generated common metadata sources are not automatically visible to JVM/native compilations, add this after the dependencies block:

```kotlin
kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
```

- [ ] **Step 2: Run KSP metadata task**

Run:

```bash
./gradlew :mcp-audio-tools:kspCommonMainKotlinMetadata
```

Expected before adding annotated tools: PASS and no generated tools, or PASS with no-op output.

- [ ] **Step 3: Commit Gradle wiring**

```bash
git add mcp-audio-tools/build.gradle.kts
git commit -m "chore: wire ksp into audio tools module"
```

---

### Task 10: Migrate Tool Definitions to Annotated Functions

**Files:**
- Create: `mcp-audio-tools/src/commonMain/kotlin/mcptool/GeneratedToolDefinitions.kt`
- Modify: `mcp-audio-tools/src/commonMain/kotlin/mcptool/TranscodeWavToMp3.kt`
- Modify: `mcp-audio-tools/src/commonMain/kotlin/mcptool/SubtitleToLrc.kt`
- Modify: `mcp-audio-tools/src/commonMain/kotlin/mcptool/ExecuteCommand.kt`

- [ ] **Step 1: Create annotated function file**

Create `mcp-audio-tools/src/commonMain/kotlin/mcptool/GeneratedToolDefinitions.kt`:

```kotlin
package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcpaudiotools.SUBTITLE_TO_LRC
import io.github.qingshu.mcpaudiotools.getEnv
import io.github.qingshu.mcpaudiotools.utils.log
import io.github.qingshu.mcptool.annotations.McpTool
import io.github.qingshu.mcptool.annotations.Required
import io.github.qingshu.mcptool.annotations.ToolParam
import io.github.qingshu.process.Process
import io.github.qingshu.process.ProcessBuilder
import io.github.qingshu.process.awaitExit
import io.github.qingshu.process.exec
import io.github.qingshu.process.stderrLines
import io.github.qingshu.process.stdoutLines
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem as fs

@McpTool(
    name = "transcode_wav_to_mp3",
    description = "Uses ffmpeg to transcode a single .wav file to .mp3. Returns the output path on success.",
)
suspend fun transcodeWavToMp3(
    @ToolParam("Absolute path to the source .wav file.") inputPath: String,
    @ToolParam("Absolute path for the output .mp3 file.") outputPath: String,
): String {
    val cmd = makeFfmpegCmd(inputPath, outputPath)
    fs.createDirectories(Path(outputPath))
    val process = ProcessBuilder(*cmd)
        .mergeStderr(true)
        .start()

    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val exitCode = coroutineScope {
        launch {
            process.stdoutLines().collect { line ->
                stdout.appendLine(line)
                log(line)
            }
        }
        launch {
            process.stderrLines().collect(stderr::appendLine)
        }
        process.awaitExit()
    }

    if (exitCode != 0) {
        error("ffmpeg failed (exit $exitCode):\n$stderr")
    }
    return "[OK] $outputPath"
}

@McpTool(
    name = "subtitle_to_lrc",
    description = "Convert a .srt or .vtt subtitle file to .lrc format.",
)
suspend fun subTitleToLrc(
    @ToolParam("Absolute path to the source .srt or .vtt file.") inputPath: String,
    @ToolParam("Absolute path for the output .lrc file.") outputPath: String,
): String {
    val cmd = getEnv(SUBTITLE_TO_LRC) ?: "subtitle_to_lrc"
    fs.createDirectories(Path(outputPath))
    val result = Process.exec(cmd, inputPath, outputPath)

    if (result.code != 0) {
        error("subtitle_to_lrc failed (exit ${result.code}):\n${result.stderr}")
    }
    return "[OK] $outputPath"
}

@McpTool(
    name = "execute_command",
    description = "Execute commands to run any executable program supported by the system, such as: 'python --version', 'ls -l'.",
)
suspend fun executeCommand(
    @ToolParam("The command to execute, e.g. 'ls -l' or 'python script.py'.") cmd: String,
    @ToolParam("Optional working directory for the command.", required = Required.FALSE) cwd: String? = null,
): String {
    val process = ProcessBuilder("bash", "-c", cmd).run {
        cwd?.let(::directory)
        start()
    }

    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val exitCode = coroutineScope {
        launch { process.stdoutLines().collect(stdout::appendLine) }
        launch { process.stderrLines().collect(stderr::appendLine) }
        process.awaitExit()
    }

    if (exitCode != 0) {
        val content = "\n- stdout: \n$stdout\n- stderr: \n$stderr"
        error("The command execute failed: $content")
    }

    return when {
        stdout.isNotEmpty() -> stdout.toString()
        else -> "[Ok] The command no output. Contact developer if unexpected."
    }
}

private fun makeFfmpegCmd(input: String, output: String): Array<String> = arrayOf(
    "ffmpeg",
    "-hide_banner",
    "-progress", "pipe:1",
    "-stats_period", "5",
    "-y",
    "-i", input,
    "-codec:a", "libmp3lame",
    "-qscale:a", "2",
    output,
)
```

- [ ] **Step 2: Remove old manual tool registration implementations**

Replace the contents of these files with package-only deprecation comments or delete the files if no external code imports their helpers:

- `mcp-audio-tools/src/commonMain/kotlin/mcptool/TranscodeWavToMp3.kt`
- `mcp-audio-tools/src/commonMain/kotlin/mcptool/SubtitleToLrc.kt`
- `mcp-audio-tools/src/commonMain/kotlin/mcptool/ExecuteCommand.kt`

Preferred: delete the three files after confirming all useful logic was copied into `GeneratedToolDefinitions.kt`.

- [ ] **Step 3: Run KSP metadata generation**

Run:

```bash
./gradlew :mcp-audio-tools:kspCommonMainKotlinMetadata
```

Expected: PASS and generated source exists under `mcp-audio-tools/build/generated/ksp/metadata/commonMain/kotlin/io/github/qingshu/mcpaudiotools/generated/GeneratedMcpTools.kt`.

- [ ] **Step 4: Inspect generated source for obvious issues**

Read:

```text
mcp-audio-tools/build/generated/ksp/metadata/commonMain/kotlin/io/github/qingshu/mcpaudiotools/generated/GeneratedMcpTools.kt
```

Expected: It contains `fun Server.registerGeneratedMcpTools()` and registrations for all three tools.

- [ ] **Step 5: Commit migrated tool definitions**

```bash
git add mcp-audio-tools/src/commonMain/kotlin/mcptool
git commit -m "feat: define audio tools with annotations"
```

---

### Task 11: Replace Manual Registry with Generated Registry

**Files:**
- Modify: `mcp-audio-tools/src/commonMain/kotlin/McpTools.kt`

- [ ] **Step 1: Update registry file**

Replace `mcp-audio-tools/src/commonMain/kotlin/McpTools.kt` with:

```kotlin
package io.github.qingshu.mcpaudiotools

import io.github.qingshu.mcpaudiotools.generated.registerGeneratedMcpTools
import io.modelcontextprotocol.kotlin.sdk.server.Server

fun Server.mcpToolRegistry() {
    registerGeneratedMcpTools()
}
```

- [ ] **Step 2: Compile JVM target**

Run:

```bash
./gradlew :mcp-audio-tools:jvmMainClasses
```

Expected: PASS. If generated sources are not visible, add the generated KSP metadata source directory to `commonMain` in `mcp-audio-tools/build.gradle.kts` as described in Task 9 and rerun.

- [ ] **Step 3: Commit registry replacement**

```bash
git add mcp-audio-tools/src/commonMain/kotlin/McpTools.kt mcp-audio-tools/build.gradle.kts
git commit -m "feat: register generated mcp tools"
```

---

### Task 12: Add Generated Registry Smoke Test

**Files:**
- Create: `mcp-audio-tools/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt`

- [ ] **Step 1: Write smoke test**

Create `mcp-audio-tools/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt`:

```kotlin
package io.github.qingshu.mcpaudiotools

import io.github.qingshu.mcpaudiotools.generated.registerGeneratedMcpTools
import kotlin.test.Test
import kotlin.test.assertNotNull

class GeneratedMcpToolsCompileTest {
    @Test
    fun `generated tools can be registered on server`() {
        val server = McpServer(
            name = "test-server",
            version = "test",
        ) {
            registerGeneratedMcpTools()
        }

        assertNotNull(server)
    }
}
```

- [ ] **Step 2: Run smoke test**

Run:

```bash
./gradlew :mcp-audio-tools:jvmTest --tests "io.github.qingshu.mcpaudiotools.GeneratedMcpToolsCompileTest"
```

Expected: PASS.

- [ ] **Step 3: Commit smoke test**

```bash
git add mcp-audio-tools/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt
git commit -m "test: cover generated mcp registry compilation"
```

---

### Task 13: Run Formatting and Full Verification

**Files:**
- Potentially modify any Kotlin/Gradle files formatted by Spotless.

- [ ] **Step 1: Run Spotless before final commit**

Because this Gradle project has Spotless configured, run:

```bash
./gradlew spotlessApply
```

Expected: PASS, files may be reformatted.

- [ ] **Step 2: Run focused verification**

Run:

```bash
./gradlew :mcp-tool-annotations:build :mcp-tool-ksp:build :mcp-audio-tools:jvmTest
```

Expected: PASS.

- [ ] **Step 3: Run full build**

Run:

```bash
./gradlew build
```

Expected: PASS. If existing platform tests fail because `ffmpeg` or local audio files are unavailable, record the exact failing tests and rerun the codegen-relevant verification from Step 2. Do not claim full build passed unless it actually passed.

- [ ] **Step 4: Commit formatting or verification fixes**

If `spotlessApply` changed files or verification required fixes:

```bash
git add <formatted-or-fixed-files>
git commit -m "style: format ksp mcp tool codegen"
```

If no files changed, do not create an empty commit.

---

### Task 14: Final Review and Handoff

**Files:**
- Read generated source under `mcp-audio-tools/build/generated/ksp/metadata/commonMain/kotlin/io/github/qingshu/mcpaudiotools/generated/GeneratedMcpTools.kt`
- Check git status.

- [ ] **Step 1: Review generated adapter behavior**

Confirm generated source includes:

```kotlin
fun Server.registerGeneratedMcpTools()
```

Confirm each existing tool appears:

```kotlin
registerExecuteCommandMcpTool()
registerSubTitleToLrcMcpTool()
registerTranscodeWavToMp3McpTool()
```

Confirm each registration uses:

```kotlin
addTool(
    name = "...",
    description = "...",
    inputSchema = ToolSchema(...),
)
```

Confirm exception handling returns `CallToolResult(..., isError = true)`.

- [ ] **Step 2: Check working tree**

Run:

```bash
git status --short
```

Expected: no uncommitted changes, unless the implementation intentionally leaves generated build outputs untracked.

- [ ] **Step 3: Summarize final state**

Report:

- Modules added.
- Existing tools migrated.
- Verification commands and results.
- Any full-build caveats, especially existing tests that require local binaries/files.

Do not claim completion until verification commands have been run and their outputs checked.

---

## Self-Review Notes

Spec coverage:

- Separate annotations and KSP modules are covered by Tasks 1-4.
- Common-first authoring and top-level annotated functions are covered by Tasks 3 and 10.
- Generated aggregate registration is covered by Tasks 8 and 11.
- Parameter supported types and requiredness validation are covered by Tasks 5-8.
- Return conversion and exception-to-error behavior are covered by Task 8 and verified in Task 14.
- Existing tool migration is covered by Tasks 10-12.
- Testing and verification are covered by Tasks 5, 6, 12, 13, and 14.

Placeholder scan: no TBD/TODO/fill-in placeholders remain. Steps include exact paths, commands, expected results, and code blocks where code changes are required.

Type consistency: plan consistently uses `@McpTool`, `@ToolParam`, `Required`, `registerGeneratedMcpTools`, `ToolFunction`, `ToolParameter`, `ParameterType`, and `ToolReturnType`.
