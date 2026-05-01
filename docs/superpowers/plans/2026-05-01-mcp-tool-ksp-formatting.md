# MCP Tool KSP Formatting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `mcp-tool-ksp` generate Kotlin source with standard four-space indentation directly from KotlinPoet, without post-generation formatting.

**Architecture:** Keep the existing generated API and runtime behavior unchanged. Refactor `ToolCodeGenerator` so `FileSpec` uses four-space indentation and indentation-sensitive code blocks use KotlinPoet indentation APIs instead of manual leading spaces.

**Tech Stack:** Kotlin JVM, KSP, KotlinPoet 2.2.0, `kotlin.test`, Gradle.

---

## File Structure

- Modify: `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`
  - Responsibility: unit tests for KSP model behavior and rendered generated code shape.
  - Add one formatting-sensitive regression test that fails with the current generator.

- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`
  - Responsibility: render the aggregate generated MCP registration source.
  - Update the file builder and code-block construction so KotlinPoet owns indentation.

No new production files are needed.

---

### Task 1: Add the failing generated-formatting regression test

**Files:**
- Modify: `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`

- [ ] **Step 1: Add the failing test**

Add this test after `generates aggregate and per tool registration functions` in `ToolModelsTest.kt`:

```kotlin
    @Test
    fun `generates registration code with standard indentation`() {
        val generated = renderGreetTool()

        val expectedRegistration = """
            public fun Server.registerGreetUserTool() {
                addTool(
                    name = "greet_user",
                    description = "Greet a user by name.",
                    inputSchema = ToolSchema(
                        properties = buildJsonObject {
                            putJsonObject("name") {
                                put("type", "string")
                                put("description", "Name to greet")
                            }
                            putJsonObject("count") {
                                put("type", "integer")
                                put("description", "How many greetings to generate")
                            }
                            putJsonObject("excited") {
                                put("type", "boolean")
                                put("description", "Whether to add emphasis")
                            }
                        },
                        required = listOf("name"),
                    ),
                ) { request ->
                    try {
                        val arguments = request.params.arguments
                        val namePresent = arguments?.containsKey("name") == true
                        val name = arguments?.get("name")?.jsonPrimitive?.contentOrNull
                        val countPresent = arguments?.containsKey("count") == true
                        val count = arguments?.get("count")?.jsonPrimitive?.intOrNull
                        val excitedPresent = arguments?.containsKey("excited") == true
                        val excited = arguments?.get("excited")?.jsonPrimitive?.booleanOrNull
                        if (namePresent && name == null) {
                            return@addTool invalidArgumentResult("name")
                        }
                        if (name == null) {
                            return@addTool missingRequiredArgumentResult("name")
                        }
                        if (countPresent && count == null) {
                            return@addTool invalidArgumentResult("count")
                        }
                        val result = invokeGreetUserTool(
                            name = name,
                            count = count,
                            countPresent = countPresent,
                            excited = excited,
                        )
                        return@addTool CallToolResult(
                            content = listOf(TextContent(result)),
                            isError = false,
                        )
                    } catch (exception: Exception) {
                        return@addTool CallToolResult(
                            content = listOf(TextContent(exception.message ?: "Tool failed")),
                            isError = true,
                        )
                    }
                }
            }
        """.trimIndent()

        assertTrue(
            actual = generated.contains(expectedRegistration),
            message = generated,
        )
        assertFalse(
            actual = generated.contains("            properties = buildJsonObject"),
            message = generated,
        )
    }
```

This test intentionally checks only one representative generated function rather than snapshotting the entire file. The final `assertFalse` guards against the current over-indented schema pattern.

- [ ] **Step 2: Run the new test and verify it fails for formatting**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates registration code with standard indentation"
```

Expected result: `FAILED` because the rendered source does not contain the four-space-indented `expectedRegistration` block. The failure output should print the generated source in the assertion message. Confirm the failure is about indentation/formatting, not a compilation error or missing test class.

- [ ] **Step 3: Check git state without committing**

Run:

```bash
git status --short
```

Expected result: `ToolModelsTest.kt` is modified. Do not commit unless the user explicitly asks for a commit.

---

### Task 2: Switch generated files to four-space KotlinPoet indentation

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`

- [ ] **Step 1: Configure `FileSpec` indentation**

In `buildFileSpec`, change the builder creation from:

```kotlin
            val builder = FileSpec.builder(GENERATED_PACKAGE, GENERATED_FILE_NAME)
                .addImport("io.modelcontextprotocol.kotlin.sdk.types", "CallToolResult", "TextContent", "ToolSchema")
```

to:

```kotlin
            val builder = FileSpec.builder(GENERATED_PACKAGE, GENERATED_FILE_NAME)
                .indent("    ")
                .addImport("io.modelcontextprotocol.kotlin.sdk.types", "CallToolResult", "TextContent", "ToolSchema")
```

This makes KotlinPoet emit four spaces for each indentation level, matching the repository's Kotlin style.

- [ ] **Step 2: Run the formatting test and verify it still fails**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates registration code with standard indentation"
```

Expected result: `FAILED`. This step proves that changing `FileSpec` indentation alone is not enough because several generated blocks still contain hard-coded leading spaces.

- [ ] **Step 3: Keep the change staged only if committing later is requested**

Run:

```bash
git diff -- mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt
```

Expected result: the diff shows only the added `.indent("    ")` call so far. Do not stage or commit unless explicitly requested.

---

### Task 3: Refactor schema and invocation expression formatting

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`

- [ ] **Step 1: Replace `buildInputSchema` with KotlinPoet indentation**

Replace the entire `buildInputSchema` function with:

```kotlin
        private fun buildInputSchema(parameters: List<ToolParameter>): CodeBlock {
            val requiredParameters = parameters.filter(ToolParameter::required)
            val code = CodeBlock.builder()
            code.add("ToolSchema(\n")
            code.indent()
            code.add("properties = buildJsonObject {\n")
            code.indent()
            parameters.forEach { parameter ->
                code.add("putJsonObject(%S) {\n", parameter.name)
                code.indent()
                code.addStatement("put(%S, %S)", "type", parameter.type.jsonSchemaType)
                code.addStatement("put(%S, %S)", "description", parameter.description)
                code.unindent()
                code.add("}\n")
            }
            code.unindent()
            code.add("},\n")
            if (requiredParameters.isEmpty()) {
                code.addStatement("required = emptyList(),")
            } else {
                code.addStatement(
                    "required = listOf(%L),",
                    requiredParameters.joinToString(", ") { "\"${it.name}\"" },
                )
            }
            code.unindent()
            code.add(")")
            return code.build()
        }
```

This removes embedded schema indentation such as `"        properties = ..."` and lets the `CodeBlock` indentation stack control nesting.

- [ ] **Step 2: Replace `buildInvocation` with KotlinPoet indentation**

Replace the entire `buildInvocation` function with:

```kotlin
        private fun buildInvocation(tool: ToolFunction, includedDefaults: Map<String, Boolean>): CodeBlock {
            val code = CodeBlock.builder()
            code.add("%L.%L(\n", tool.packageName, tool.functionName)
            code.indent()
            tool.parameters.forEach { parameter ->
                if (parameter.hasDefault && includedDefaults[parameter.name] == false) return@forEach
                code.addStatement("%N = %L,", parameter.name, buildInvocationArgument(parameter))
            }
            code.unindent()
            code.add(")")
            return code.build()
        }
```

This preserves all invocation arguments and default-argument omission behavior while removing hard-coded four-space argument indentation.

- [ ] **Step 3: Run the formatting test and verify it still fails**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates registration code with standard indentation"
```

Expected result: `FAILED`. The schema and invocation expressions should now look closer to the expected output, but the outer `addTool`, result, and catch blocks still contain manual leading spaces.

---

### Task 4: Refactor registration body and result formatting

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`

- [ ] **Step 1: Replace `buildAddToolBlock` with structured indentation**

Replace the entire `buildAddToolBlock` function with:

```kotlin
        private fun buildAddToolBlock(tool: ToolFunction, generatedNames: GeneratedToolNames): CodeBlock {
            val code = CodeBlock.builder()
            code.add("addTool(\n")
            code.indent()
            code.addStatement("name = %S,", tool.toolName)
            code.addStatement("description = %S,", tool.description)
            code.addStatement("inputSchema = %L,", buildInputSchema(tool.parameters))
            code.unindent()
            code.add(") { request ->\n")
            code.indent()
            code.beginControlFlow("try")
            code.addStatement("val arguments = request.params.arguments")

            tool.parameters.forEach { parameter ->
                code.addStatement("val %NPresent = arguments?.containsKey(%S) == true", parameter.name, parameter.name)
                code.addStatement(
                    "val %N = arguments?.get(%S)?.jsonPrimitive?.%L",
                    parameter.name,
                    parameter.name,
                    parameter.type.accessorName(),
                )
            }

            tool.parameters.forEach { parameter ->
                if (!parameter.nullable) {
                    code.beginControlFlow("if (%NPresent && %N == null)", parameter.name, parameter.name)
                    code.addStatement("return@addTool invalidArgumentResult(%S)", parameter.name)
                    code.endControlFlow()
                }
                if (parameter.required) {
                    code.beginControlFlow("if (%N == null)", parameter.name)
                    code.addStatement("return@addTool missingRequiredArgumentResult(%S)", parameter.name)
                    code.endControlFlow()
                }
            }

            if (tool.parameters.any(ToolParameter::hasDefault)) {
                code.add("val result = %N(\n", generatedNames.invocationHelperName(tool))
                code.indent()
                tool.parameters.forEach { parameter ->
                    code.addStatement("%N = %N,", parameter.name, parameter.name)
                    if (parameter.hasDefault) {
                        code.addStatement("%NPresent = %NPresent,", parameter.name, parameter.name)
                    }
                }
                code.unindent()
                code.add(")\n")
            } else {
                code.addStatement("val result = %L", buildInvocation(tool, tool.parameters.associate { it.name to true }))
            }

            code.add(buildResultHandling(tool))
            code.nextControlFlow("catch (exception: Exception)")
            code.add("return@addTool CallToolResult(\n")
            code.indent()
            code.addStatement("content = listOf(TextContent(exception.message ?: %S)),", "Tool failed")
            code.addStatement("isError = true,")
            code.unindent()
            code.add(")\n")
            code.endControlFlow()
            code.unindent()
            code.add("}\n")
            return code.build()
        }
```

This keeps the current generated behavior but removes manual spaces from the `addTool`, `try/catch`, validation, and default-helper invocation blocks.

- [ ] **Step 2: Replace `buildResultHandling` with structured indentation**

Replace the entire `buildResultHandling` function with:

```kotlin
        private fun buildResultHandling(tool: ToolFunction): CodeBlock {
            val code = CodeBlock.builder()
            when (tool.returnType) {
                ToolReturnType.TextType -> {
                    code.add("return@addTool CallToolResult(\n")
                    code.indent()
                    code.addStatement("content = listOf(TextContent(result)),")
                    code.addStatement("isError = false,")
                    code.unindent()
                    code.add(")\n")
                }

                ToolReturnType.PrimitiveType -> {
                    code.add("return@addTool CallToolResult(\n")
                    code.indent()
                    code.addStatement("content = listOf(TextContent(result.toString())),")
                    code.addStatement("isError = false,")
                    code.unindent()
                    code.add(")\n")
                }

                ToolReturnType.UnitType -> {
                    code.add("return@addTool CallToolResult(\n")
                    code.indent()
                    code.addStatement("content = listOf(TextContent(%S)),", "[OK]")
                    code.addStatement("isError = false,")
                    code.unindent()
                    code.add(")\n")
                }

                ToolReturnType.CallToolResultType -> code.addStatement("return@addTool result")
            }
            return code.build()
        }
```

This preserves result conversion for `String`, primitive, `Unit`, and direct `CallToolResult` returns.

- [ ] **Step 3: Run the formatting test and verify it still fails or passes**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates registration code with standard indentation"
```

Expected result: likely `FAILED` if helper result functions still differ from expected whole-file formatting, or `PASSED` if the registration block now matches. If it fails, confirm the failure is still a formatting mismatch, not a semantic change.

---

### Task 5: Refactor generated error helper formatting

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`

- [ ] **Step 1: Replace `buildMissingRequiredArgumentResultFunction`**

Replace the entire `buildMissingRequiredArgumentResultFunction` with:

```kotlin
        private fun buildMissingRequiredArgumentResultFunction(): FunSpec = FunSpec.builder("missingRequiredArgumentResult")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("name", stringType)
            .returns(callToolResultType)
            .addCode(
                CodeBlock.builder()
                    .add("return CallToolResult(\n")
                    .indent()
                    .addStatement("content = listOf(TextContent(\"Missing required argument: \$name\")),")
                    .addStatement("isError = true,")
                    .unindent()
                    .add(")\n")
                    .build(),
            )
            .build()
```

This keeps the generated runtime string interpolation for the helper's `name` parameter.

- [ ] **Step 2: Replace `buildInvalidArgumentResultFunction`**

Replace the entire `buildInvalidArgumentResultFunction` with:

```kotlin
        private fun buildInvalidArgumentResultFunction(): FunSpec = FunSpec.builder("invalidArgumentResult")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("name", stringType)
            .returns(callToolResultType)
            .addCode(
                CodeBlock.builder()
                    .add("return CallToolResult(\n")
                    .indent()
                    .addStatement("content = listOf(TextContent(\"Invalid argument: \$name\")),")
                    .addStatement("isError = true,")
                    .unindent()
                    .add(")\n")
                    .build(),
            )
            .build()
```

This keeps the generated runtime string interpolation for the helper's `name` parameter.

- [ ] **Step 3: Run the formatting test and verify it passes**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest.generates registration code with standard indentation"
```

Expected result: `PASSED`.

- [ ] **Step 4: If the test fails because exact expected formatting differs only by harmless blank lines**

Update the test's `expectedRegistration` string to match KotlinPoet's stable final rendering, but do not weaken the assertions to ignore indentation. The test must still assert four-space indentation for the representative registration block and must still reject `"            properties = buildJsonObject"`.

---

### Task 6: Run existing KSP tests and fix behavior regressions

**Files:**
- Modify only if tests require it:
  - `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`
  - `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`

- [ ] **Step 1: Run the full KSP module test suite**

Run:

```bash
./gradlew :mcp-tool-ksp:test
```

Expected result: `BUILD SUCCESSFUL`.

- [ ] **Step 2: If `TextContent("Missing required argument: $name")` assertions fail**

Keep generated behavior unchanged. The generated code should contain runtime interpolation, not a literal dollar sign. The output should still contain these substrings from the existing tests:

```kotlin
TextContent("Invalid argument: $name")
TextContent("Missing required argument: $name")
```

If KotlinPoet escapes the dollar sign differently but produces equivalent Kotlin, update only the existing string-shape assertions to accept the stable KotlinPoet rendering. Do not change runtime behavior.

- [ ] **Step 3: If old substring tests fail because indentation changed but behavior did not**

Prefer preserving old behavior assertions by matching behavior-relevant substrings without leading spaces. For example, keep assertions such as:

```kotlin
assertTrue(generated.contains("val result = invokeGreetUserTool"))
assertTrue(generated.contains("count = count!!"))
assertTrue(generated.contains("TextContent(result)"))
```

Do not remove behavior assertions. Only adjust assertions that assumed the previous bad indentation.

- [ ] **Step 4: Run git diff to inspect the complete change**

Run:

```bash
git diff -- mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt
```

Expected result: the diff shows one new formatting regression test and `ToolCodeGenerator` indentation refactors. No generated API names, package names, validation rules, or runtime behavior should change.

---

### Task 7: Run repository formatting and relevant broader verification

**Files:**
- Potentially modified by formatting:
  - `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`
  - `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`

- [ ] **Step 1: Apply repository formatting**

Run:

```bash
./gradlew spotlessApply
```

Expected result: `BUILD SUCCESSFUL`. This formats source files in the repository. It does not format generated output as part of the KSP generator; it only formats the handwritten files changed by this task.

- [ ] **Step 2: Re-run the KSP module tests after formatting**

Run:

```bash
./gradlew :mcp-tool-ksp:test
```

Expected result: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run the full test suite if time permits**

Run:

```bash
./gradlew test
```

Expected result: `BUILD SUCCESSFUL`. If this fails in unrelated modules because required external binaries are missing, record the exact failure and keep `:mcp-tool-ksp:test` as the primary verification for this change.

- [ ] **Step 4: Check final working tree state**

Run:

```bash
git status --short
```

Expected result: only intentional files are modified:

```text
 M mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt
 M mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt
?? docs/superpowers/specs/2026-05-01-mcp-tool-ksp-formatting-design.md
?? docs/superpowers/plans/2026-05-01-mcp-tool-ksp-formatting.md
```

The exact status may include the spec and plan if they were not already tracked. Do not commit unless the user explicitly asks for a commit.

---

## Self-Review

- Spec coverage: covered direct generation formatting, no post-generation formatter, KotlinPoet-owned indentation, tests-first workflow, behavior preservation, and relevant Gradle verification.
- Placeholder scan: no `TBD`, `TODO`, `implement later`, or vague test/implementation instructions remain.
- Type consistency: function names, paths, and Kotlin model names match existing `ToolCodeGenerator.kt` and `ToolModelsTest.kt`.
