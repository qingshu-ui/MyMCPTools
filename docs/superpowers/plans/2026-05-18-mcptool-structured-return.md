# MCP Tool Structured Return Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable `@McpTool` functions to return `@Serializable` custom types as MCP `structuredContent`.

**Architecture:** Add a new `ToolReturnType.SerializableStructuredType` model that carries the generated Kotlin return type. Validation keeps existing built-in return handling and accepts otherwise unsupported types only when the declaration has `@Serializable`. Code generation serializes structured return values with `Json.encodeToJsonElement` and places the JSON element in `CallToolResult.structuredContent`.

**Tech Stack:** Kotlin 2.3.21, KotlinPoet, KSP, kotlinx.serialization JSON, Model Context Protocol Kotlin SDK, Gradle, kotlin.test.

---

## File Structure

- Modify `.gitignore`: ignore sandbox-generated shell home/config files that can appear in the repository root.
- Modify `mcp-tool-ksp/src/main/kotlin/ToolModels.kt`: add `SerializableStructuredType` to the tool return model.
- Modify `mcp-tool-ksp/src/main/kotlin/ToolValidator.kt`: accept custom returns annotated with `@Serializable` and reject non-serializable custom returns with a clearer error.
- Modify `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt`: generate imports, helper return types, and result handling for structured content.
- Modify `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`: add rendering tests for structured return generation.
- Modify `mcp-tool-ksp/src/test/kotlin/ToolValidatorTest.kt`: add validator tests for serializable and non-serializable custom returns.
- Use existing `essential-mcp/src/commonMain/kotlin/mcptool/McpDemo.kt` as integration coverage through KSP compilation.

---

### Task 1: Ignore sandbox home files

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Verify the sandbox files are ignored**

Run:

```bash
git status --short
```

Expected: the sandbox dotfiles such as `.bashrc`, `.gitconfig`, `.mcp.json`, and `.vscode` do not appear. The relevant visible files are `.gitignore`, the design spec, and `essential-mcp/src/commonMain/kotlin/mcptool/McpDemo.kt`.

- [ ] **Step 2: Commit the ignore update only**

```bash
git add .gitignore
git commit -m "chore: ignore sandbox home files"
```

Expected: commit succeeds and does not include the spec or Kotlin source changes.

---

### Task 2: Add structured return model

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolModels.kt:95-103`
- Test: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Write the failing rendering test**

Add this test method inside `ToolModelsTest`:

```kotlin
@Test
fun `generates structured content for serializable custom return type`() {
    val generated = ToolCodeGenerator.render(
        tools = listOf(
            ToolFunction(
                packageName = "com.example.tools",
                functionName = "status",
                toolName = "status",
                description = "Return command status.",
                isSuspend = false,
                parameters = emptyList(),
                returnType = ToolReturnType.SerializableStructuredType(ClassName("com.example.tools", "ToolResult")),
            ),
        ),
    )

    assertTrue(generated.contains("import kotlinx.serialization.json.Json"), generated)
    assertTrue(generated.contains("import kotlinx.serialization.json.encodeToJsonElement"), generated)
    assertTrue(generated.contains("val result = com.example.tools.status("), generated)
    assertTrue(generated.contains("structuredContent = Json.encodeToJsonElement(result),"), generated)
    assertTrue(generated.contains("content = emptyList(),"), generated)
}
```

Also add this import near the top of the file:

```kotlin
import com.squareup.kotlinpoet.ClassName
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates structured content for serializable custom return type"
```

Expected: FAIL because `ToolReturnType.SerializableStructuredType` does not exist.

- [ ] **Step 3: Implement the model**

In `ToolModels.kt`, replace the `ToolReturnType` sealed class with:

```kotlin
sealed class ToolReturnType {
    data object UnitType : ToolReturnType()

    data object TextType : ToolReturnType()

    data object PrimitiveType : ToolReturnType()

    data object CallToolResultType : ToolReturnType()

    data class SerializableStructuredType(
        val typeName: TypeName,
    ) : ToolReturnType()
}
```

Add this import:

```kotlin
import com.squareup.kotlinpoet.TypeName
```

- [ ] **Step 4: Run the test to verify the failure advances**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates structured content for serializable custom return type"
```

Expected: FAIL because code generation does not handle `SerializableStructuredType` yet.

---

### Task 3: Generate structured content results

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt:84-95`
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt:389-431`
- Test: `mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt`

- [ ] **Step 1: Add JSON serialization imports to generated files**

In `ToolCodeGenerator.buildFileSpec`, extend the existing `kotlinx.serialization.json` import list so it includes `Json` and `encodeToJsonElement`:

```kotlin
.addImport(
    "kotlinx.serialization.json",
    "Json",
    "booleanOrNull",
    "buildJsonObject",
    "contentOrNull",
    "doubleOrNull",
    "encodeToJsonElement",
    "intOrNull",
    "jsonPrimitive",
    "longOrNull",
    "put",
    "putJsonObject",
)
```

- [ ] **Step 2: Add structured result handling**

In `buildResultHandling`, add this branch before the `CallToolResultType` branch:

```kotlin
ToolReturnType.SerializableStructuredType -> {
    code.add("return@addTool CallToolResult(\n")
    code.indent()
    code.addStatement("content = emptyList(),")
    code.addStatement("structuredContent = Json.encodeToJsonElement(result),")
    code.addStatement("isError = false,")
    code.unindent()
    code.add(")\n")
}
```

- [ ] **Step 3: Return the custom type from invocation helpers**

In `ToolReturnType.generatedReturnType`, add this branch:

```kotlin
is ToolReturnType.SerializableStructuredType -> typeName
```

- [ ] **Step 4: Run the rendering test**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates structured content for serializable custom return type"
```

Expected: PASS.

- [ ] **Step 5: Run all model rendering tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: PASS.

- [ ] **Step 6: Commit codegen support**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolModels.kt mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt
git commit -m "feat: generate structured tool results"
```

Expected: commit succeeds.

---

### Task 4: Validate `@Serializable` custom returns

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolValidator.kt:73-76`
- Modify: `mcp-tool-ksp/src/main/kotlin/ToolValidator.kt:269-289`
- Test: `mcp-tool-ksp/src/test/kotlin/ToolValidatorTest.kt`

- [ ] **Step 1: Inspect existing validator test helpers**

Read `mcp-tool-ksp/src/test/kotlin/ToolValidatorTest.kt` and reuse its compile helper pattern for KSP validation errors.

- [ ] **Step 2: Write a failing validation success test**

Add a test that compiles a source containing:

```kotlin
package com.example.tools

import io.github.qingshu.mcptool.annotations.McpTool
import kotlinx.serialization.Serializable

@Serializable
data class ToolResult(val status: String)

@McpTool(name = "status", description = "Return status.")
fun status(): ToolResult = ToolResult("ok")
```

Assert compilation succeeds. Use the existing test helper style in `ToolValidatorTest.kt`.

- [ ] **Step 3: Write a failing validation error test**

Add a test that compiles a source containing:

```kotlin
package com.example.tools

import io.github.qingshu.mcptool.annotations.McpTool

data class ToolResult(val status: String)

@McpTool(name = "status", description = "Return status.")
fun status(): ToolResult = ToolResult("ok")
```

Assert compilation fails and the diagnostics contain:

```text
Custom return types must be annotated with @Serializable to be emitted as structuredContent.
```

Use the existing diagnostics assertion style in `ToolValidatorTest.kt`.

- [ ] **Step 4: Run validator tests to verify failure**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolValidatorTest"
```

Expected: FAIL because validation still rejects all custom returns.

- [ ] **Step 5: Add constants and helper functions**

In `ToolValidator.kt`, add this constant near `CALL_TOOL_RESULT`:

```kotlin
private const val SERIALIZABLE = "kotlinx.serialization.Serializable"
```

Add these helper functions near `resolveReturnType`:

```kotlin
private fun KSType.hasSerializableAnnotation(): Boolean = declaration.annotations.any {
    it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIALIZABLE
}

private fun KSType.generatedTypeName(): TypeName = toTypeName()
```

Add these imports:

```kotlin
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
```

- [ ] **Step 6: Implement serializable return validation**

Replace the `else` branch in `resolveReturnType` with:

```kotlin
else -> {
    if (resolved != null && resolved.hasSerializableAnnotation()) {
        ToolReturnType.SerializableStructuredType(resolved.generatedTypeName())
    } else {
        logger.error(
            "Unsupported @McpTool return type '$qualifiedName'. Supported returns: Unit, String, Int, Long, Double, Boolean, CallToolResult. " +
                "Custom return types must be annotated with @Serializable to be emitted as structuredContent.",
            this,
        )
        null
    }
}
```

- [ ] **Step 7: Run validator tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolValidatorTest"
```

Expected: PASS.

- [ ] **Step 8: Commit validation support**

```bash
git add mcp-tool-ksp/src/main/kotlin/ToolValidator.kt mcp-tool-ksp/src/test/kotlin/ToolValidatorTest.kt
git commit -m "feat: validate serializable tool returns"
```

Expected: commit succeeds.

---

### Task 5: Verify integration with `McpDemo.kt`

**Files:**
- Use: `essential-mcp/src/commonMain/kotlin/mcptool/McpDemo.kt`
- Modify if formatter requires: `essential-mcp/src/commonMain/kotlin/mcptool/McpDemo.kt`

- [ ] **Step 1: Run KSP metadata generation**

Run:

```bash
./gradlew :essential-mcp:kspCommonMainKotlinMetadata
```

Expected: PASS. The previous unsupported return type error for `ToolResult` should be gone.

- [ ] **Step 2: Run focused KSP and integration tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test :essential-mcp:compileKotlinMetadata
```

Expected: PASS.

- [ ] **Step 3: Run formatting**

Run:

```bash
./gradlew spotlessApply
```

Expected: PASS. Spotless may format `McpDemo.kt` or modified KSP files.

- [ ] **Step 4: Run final verification**

Run:

```bash
./gradlew :mcp-tool-ksp:test :essential-mcp:compileKotlinMetadata spotlessCheck
```

Expected: PASS.

- [ ] **Step 5: Commit integration and formatting changes**

```bash
git add essential-mcp/src/commonMain/kotlin/mcptool/McpDemo.kt mcp-tool-ksp/src/main/kotlin/ToolModels.kt mcp-tool-ksp/src/main/kotlin/ToolValidator.kt mcp-tool-ksp/src/main/kotlin/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/ToolModelsTest.kt mcp-tool-ksp/src/test/kotlin/ToolValidatorTest.kt
git commit -m "test: cover structured tool return integration"
```

Expected: commit succeeds if files changed after the prior commits. If there are no staged changes, skip this commit.

---

### Task 6: Commit planning documents

**Files:**
- Create: `docs/superpowers/specs/2026-05-18-mcptool-structured-return-design.md`
- Create: `docs/superpowers/plans/2026-05-18-mcptool-structured-return.md`

- [ ] **Step 1: Review changed files**

Run:

```bash
git status --short
git diff -- docs/superpowers/specs/2026-05-18-mcptool-structured-return-design.md docs/superpowers/plans/2026-05-18-mcptool-structured-return.md
```

Expected: only the spec and plan documents are shown as untracked or modified for this task.

- [ ] **Step 2: Commit docs**

```bash
git add docs/superpowers/specs/2026-05-18-mcptool-structured-return-design.md docs/superpowers/plans/2026-05-18-mcptool-structured-return.md
git commit -m "docs: plan structured tool returns"
```

Expected: commit succeeds.

---

### Task 7: Final branch verification

**Files:**
- No source edits expected.

- [ ] **Step 1: Run full relevant verification**

Run:

```bash
./gradlew build spotlessCheck
```

Expected: PASS.

- [ ] **Step 2: Check working tree**

Run:

```bash
git status --short
```

Expected: no uncommitted changes except user-owned files intentionally left untracked.

- [ ] **Step 3: Report completion**

Summarize:

```text
Implemented @Serializable custom @McpTool returns as structuredContent, verified with KSP tests, metadata compilation, full build, and Spotless.
```

Do not claim success unless the commands in this task passed.
