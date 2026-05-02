# KSP Generated MCP Tools Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix generated MCP tool Kotlin so schema names can stay snake_case while Kotlin identifiers are camelCase, direct generated calls are correctly indented, and unnecessary non-null assertions are not emitted after same-scope null guards.

**Architecture:** Keep `ToolParameter.name` as the Kotlin source identifier and add `ToolParameter.schemaName` for the external MCP property name. The generator uses `schemaName` for JSON schema, argument lookup, and user-facing argument errors, while it uses `name` for generated local variables and Kotlin invocation arguments. Non-null assertions remain available for generated helper functions because nullable values cross a function boundary there; direct invocations omit them after the generated guard clauses have smart-cast the values.

**Tech Stack:** Kotlin Multiplatform, KSP, KotlinPoet, kotlin.test, Gradle, Spotless/ktlint.

**Commit note:** Do not create git commits during execution unless the user explicitly asks for commits.

---

## File Structure

- Modify `mcp-tool-annotations/src/commonMain/kotlin/io/github/qingshu/mcptool/annotations/ToolParam.kt`
  - Adds optional `name` alias used for MCP schema/request property names.
- Modify `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolModels.kt`
  - Adds `schemaName` to `ToolParameter`.
- Modify `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolValidator.kt`
  - Reads `@ToolParam(name = "...")`, falling back to the Kotlin parameter name.
- Modify `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`
  - Uses schema names externally, Kotlin names internally, fixes direct-call formatting, and controls non-null assertion generation by call context.
- Modify `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`
  - Updates existing model constructors and adds coverage for schema aliases, direct-call indentation, and non-null assertions.
- Modify `mcp-audio-tools/src/commonMain/kotlin/mcptool/SubtitleToLrc.kt`
  - Renames `input_path`/`output_path` to `inputPath`/`outputPath`, preserving schema aliases.
- Modify `mcp-audio-tools/src/commonMain/kotlin/mcptool/TranscodeWavToMp3.kt`
  - Renames `input_path`/`output_path` to `inputPath`/`outputPath`, preserving schema aliases.
- Keep `mcp-audio-tools/src/commonMain/kotlin/mcptool/ExecuteCommand.kt` unchanged unless formatting changes are applied by Spotless; its parameters already use idiomatic Kotlin names and compatible schema names.
- Validate `mcp-audio-tools/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt`
  - Existing assertions should keep passing because schema aliases preserve `input_path` and `output_path`.

---

### Task 1: Add failing generator coverage for schema aliases, direct-call indentation, and direct-call non-null assertions

**Files:**
- Modify: `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`

- [ ] **Step 1: Add a schema-name assertion to the generated registration test**

In `generates aggregate and per tool registration functions`, after the existing required-list assertion, add these assertions:

```kotlin
assertTrue(generated.contains("putJsonObject(\"display_name\")"))
assertTrue(generated.contains("required = listOf(\"display_name\")"))
assertTrue(generated.contains("val namePresent = arguments?.containsKey(\"display_name\") == true"))
assertTrue(generated.contains("val name = arguments?.get(\"display_name\")?.jsonPrimitive?.contentOrNull"))
```

Replace the old assertions that expect `"name"` as the schema/request key:

```kotlin
assertTrue(generated.contains("required = listOf(\"name\")"))
assertTrue(generated.contains("val name = arguments?.get(\"name\")?.jsonPrimitive?.contentOrNull"))
```

with the new `display_name` assertions above.

- [ ] **Step 2: Update the indentation expected block to use the aliased schema key and no helper-call assertions in the registration body**

In `generates registration code with standard indentation`, replace every external `"name"` key in the expected registration block with `"display_name"`, but keep the Kotlin local variable `name`:

```kotlin
putJsonObject("display_name") {
    put("type", "string")
    put("description", "Name to greet")
}
```

```kotlin
required = listOf("display_name"),
```

```kotlin
val namePresent = arguments?.containsKey("display_name") == true
val name = arguments?.get("display_name")?.jsonPrimitive?.contentOrNull
```

```kotlin
return@addTool invalidArgumentResult("display_name")
```

```kotlin
return@addTool missingRequiredArgumentResult("display_name")
```

Keep this helper invocation shape in the same expected block because the defaulted `count` parameter still requires the helper:

```kotlin
val result = invokeGreetUserTool(
    name = name,
    count = count,
    countPresent = countPresent,
    excited = excited,
)
```

- [ ] **Step 3: Add a direct-call formatting and non-null assertion test**

Add this test below `generates non suspend invocation directly when no defaults exist`:

```kotlin
@Test
fun `direct generated invocation uses standard indentation and smart casted arguments`() {
    val generated = renderNumericTool()

    val expectedInvocation = """
        val result = com.example.tools.measure(
            ratio = ratio,
        )
    """.trimIndent()

    assertTrue(
        actual = generated.contains(expectedInvocation),
        message = generated,
    )
    assertFalse(
        actual = generated.contains("ratio = ratio!!"),
        message = generated,
    )
}
```

- [ ] **Step 4: Update `renderGreetTool()` model data to include an alias for the Kotlin `name` parameter**

In the first `ToolParameter` inside `renderGreetTool()`, add `schemaName = "display_name"` immediately after `name = "name"`:

```kotlin
ToolParameter(
    name = "name",
    schemaName = "display_name",
    description = "Name to greet",
    type = ParameterType.StringType,
    nullable = false,
    hasDefault = false,
    required = true,
)
```

For every other `ToolParameter` literal in this test file, add `schemaName` equal to `name`, for example:

```kotlin
ToolParameter(
    name = "count",
    schemaName = "count",
    description = "How many greetings to generate",
    type = ParameterType.IntType,
    nullable = false,
    hasDefault = true,
    required = false,
)
```

- [ ] **Step 5: Run the focused KSP tests and verify they fail for the expected reason**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: FAIL before implementation because `ToolParameter` does not yet accept `schemaName`, and/or generated output still uses `name` as the schema key and emits `ratio!!`.

---

### Task 2: Add schema-name support to annotations, models, and validation

**Files:**
- Modify: `mcp-tool-annotations/src/commonMain/kotlin/io/github/qingshu/mcptool/annotations/ToolParam.kt`
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolModels.kt`
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolValidator.kt`
- Test: `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`

- [ ] **Step 1: Extend `ToolParam` without breaking existing positional description usage**

Change `ToolParam` to:

```kotlin
public annotation class ToolParam(
    public val description: String,
    public val required: Required = Required.UNSPECIFIED,
    public val name: String = "",
)
```

- [ ] **Step 2: Add `schemaName` to `ToolParameter`**

Change `ToolParameter` to:

```kotlin
data class ToolParameter(
    val name: String,
    val schemaName: String,
    val description: String,
    val type: ParameterType,
    val nullable: Boolean,
    val hasDefault: Boolean,
    val required: Boolean,
)
```

- [ ] **Step 3: Read the optional annotation name in `ToolValidator`**

In `toToolParameterOrNull`, after validating `description`, add:

```kotlin
val schemaName = annotation.argumentValue<String>("name")
    .orEmpty()
    .ifBlank { parameterName }
```

Then pass it into `ToolParameter`:

```kotlin
return ToolParameter(
    name = parameterName,
    schemaName = schemaName,
    description = description,
    type = parameterType,
    nullable = resolvedType.isMarkedNullable,
    hasDefault = hasDefault,
    required = required,
)
```

- [ ] **Step 4: Run the focused KSP tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: tests still fail because the generator has not been updated to use `schemaName` or omit direct-call `!!` yet.

---

### Task 3: Update code generation to use schema names externally and Kotlin names internally

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`
- Test: `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`

- [ ] **Step 1: Use schema names when reading request arguments and reporting argument errors**

In `buildAddToolBlock`, replace the request lookup and error-name arguments so external keys use `parameter.schemaName`:

```kotlin
code.addStatement("val %NPresent = arguments?.containsKey(%S) == true", parameter.name, parameter.schemaName)
code.addStatement(
    "val %N = arguments?.get(%S)?.jsonPrimitive?.%L",
    parameter.name,
    parameter.schemaName,
    parameter.type.accessorName(),
)
```

```kotlin
code.addStatement("return@addTool invalidArgumentResult(%S)", parameter.schemaName)
```

```kotlin
code.addStatement("return@addTool missingRequiredArgumentResult(%S)", parameter.schemaName)
```

- [ ] **Step 2: Use schema names in the input schema**

In `buildInputSchema`, replace `parameter.name` with `parameter.schemaName` for JSON property names and required-list values:

```kotlin
code.add("putJsonObject(%S) {\n", parameter.schemaName)
```

```kotlin
requiredParameters.joinToString(", ") { "\"${it.schemaName}\"" }
```

- [ ] **Step 3: Keep Kotlin names for locals, helper parameters, and function invocation arguments**

Verify these existing uses stay as `parameter.name`:

```kotlin
code.addStatement("val %NPresent = ...", parameter.name, ...)
code.addStatement("val %N = ...", parameter.name, ...)
builder.addParameter(ParameterSpec.builder(parameter.name, parameter.generatedParameterType()).build())
builder.addParameter(ParameterSpec.builder("${parameter.name}Present", booleanType).build())
code.addStatement("%N = %N,", parameter.name, parameter.name)
code.addStatement("%NPresent = %NPresent,", parameter.name, parameter.name)
code.add("%N = %L,\n", parameter.name, buildInvocationArgument(...))
```

- [ ] **Step 4: Run the focused KSP tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: schema alias assertions pass; direct-call indentation or `ratio!!` assertions may still fail until Task 4.

---

### Task 4: Fix direct invocation indentation and non-null assertion context

**Files:**
- Modify: `mcp-tool-ksp/src/main/kotlin/io/github/qingshu/mcptool/ksp/ToolCodeGenerator.kt`
- Test: `mcp-tool-ksp/src/test/kotlin/io/github/qingshu/mcptool/ksp/ToolModelsTest.kt`

- [ ] **Step 1: Add an invocation argument mode**

Inside the companion object in `ToolCodeGenerator`, add:

```kotlin
private enum class NonNullAssertionMode {
    SmartCasted,
    Required,
}
```

- [ ] **Step 2: Thread the assertion mode through `buildInvocation`**

Change the signature to:

```kotlin
private fun buildInvocation(
    tool: ToolFunction,
    includedDefaults: Map<String, Boolean>,
    nonNullAssertionMode: NonNullAssertionMode,
): CodeBlock {
```

Update the loop to pass the mode:

```kotlin
code.add("%N = %L,\n", parameter.name, buildInvocationArgument(parameter, nonNullAssertionMode))
```

- [ ] **Step 3: Use `SmartCasted` for same-scope direct calls and `Required` for helper calls**

In `buildAddToolBlock`, update the no-default direct call:

```kotlin
code.addStatement(
    "val result = %L",
    buildInvocation(
        tool = tool,
        includedDefaults = tool.parameters.associate { it.name to true },
        nonNullAssertionMode = NonNullAssertionMode.SmartCasted,
    ),
)
```

In `buildInvocationBranches`, update the helper return call:

```kotlin
code.addStatement(
    "return %L",
    buildInvocation(
        tool = tool,
        includedDefaults = includedDefaults,
        nonNullAssertionMode = NonNullAssertionMode.Required,
    ),
)
```

- [ ] **Step 4: Change `buildInvocationArgument` to omit `!!` only for direct smart-casted contexts**

Replace `buildInvocationArgument` with:

```kotlin
private fun buildInvocationArgument(
    parameter: ToolParameter,
    nonNullAssertionMode: NonNullAssertionMode,
): CodeBlock = when {
    parameter.nullable -> CodeBlock.of("%N", parameter.name)
    nonNullAssertionMode == NonNullAssertionMode.SmartCasted -> CodeBlock.of("%N", parameter.name)
    else -> CodeBlock.of("%N!!", parameter.name)
}
```

- [ ] **Step 5: Fix direct-call continuation indentation if the new test still fails**

If `direct generated invocation uses standard indentation and smart casted arguments` still shows over-indented arguments, change `buildInvocation` to avoid adding an extra KotlinPoet indent level on top of `addStatement`. The intended rendered direct call is exactly:

```kotlin
val result = com.example.tools.measure(
    ratio = ratio,
)
```

The helper branch must still render valid Kotlin like:

```kotlin
return com.example.tools.greet(
    name = name!!,
    count = count!!,
    excited = excited,
)
```

- [ ] **Step 6: Run the focused KSP tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test --tests "io.github.qingshu.mcptool.ksp.ToolModelsTest"
```

Expected: PASS.

---

### Task 5: Rename audio tool source parameters to camelCase while preserving MCP schema names

**Files:**
- Modify: `mcp-audio-tools/src/commonMain/kotlin/mcptool/SubtitleToLrc.kt`
- Modify: `mcp-audio-tools/src/commonMain/kotlin/mcptool/TranscodeWavToMp3.kt`
- Test: `mcp-audio-tools/src/commonTest/kotlin/GeneratedMcpToolsCompileTest.kt`

- [ ] **Step 1: Update `SubtitleToLrc.kt` parameters and usages**

Change the function signature and body to:

```kotlin
suspend fun subTitleToLrc(
    @ToolParam(description = "Absolute path to the source .srt or .vtt file", name = "input_path")
    inputPath: String,
    @ToolParam(description = "Absolute path for the output .lrc file.", name = "output_path")
    outputPath: String,
): String {
    val cmd = getEnv(SUBTITLE_TO_LRC) ?: "subtitle_to_lrc"
    createParentDirectories(outputPath)
    val result = Process.exec(cmd, inputPath, outputPath)

    if (result.code == 0) {
        return "[OK] $outputPath"
    }

    error("[Failed] subtitle_to_lrc failed (exit ${result.code}): \n${result.stderr}")
}
```

- [ ] **Step 2: Update `TranscodeWavToMp3.kt` parameters and usages**

Change the function signature and the first lines of the body to:

```kotlin
suspend fun transcodeWavToMp3(
    @ToolParam(description = "Absolute path to the source .wav file.", name = "input_path")
    inputPath: String,
    @ToolParam(description = "Absolute path for the output .mp3 file.", name = "output_path")
    outputPath: String,
): String {
    val cmd = makeFfmpegCmd(inputPath, outputPath)
    createParentDirectories(outputPath)
```

Also update the success return:

```kotlin
return "[OK] $outputPath"
```

- [ ] **Step 3: Run audio compile tests**

Run:

```bash
./gradlew :mcp-audio-tools:test --tests "io.github.qingshu.mcpaudiotools.GeneratedMcpToolsCompileTest"
```

Expected: PASS, including existing required schema names `input_path` and `output_path`.

---

### Task 6: Run formatting and full verification

**Files:**
- Modify if needed: files touched by Spotless formatting

- [ ] **Step 1: Apply project formatting**

Run:

```bash
./gradlew spotlessApply
```

Expected: SUCCESS. Spotless may rewrite formatting in touched Kotlin files.

- [ ] **Step 2: Run targeted tests**

Run:

```bash
./gradlew :mcp-tool-ksp:test :mcp-audio-tools:test
```

Expected: SUCCESS.

- [ ] **Step 3: Run full build if targeted tests pass**

Run:

```bash
./gradlew build
```

Expected: SUCCESS.

- [ ] **Step 4: Inspect generated output for the original reported cases**

Open or read the generated file at:

```text
mcp-audio-tools/build/generated/ksp/metadata/commonMain/kotlin/io/github/qingshu/mcptool/generated/GeneratedMcpTools.kt
```

Confirm it contains the desired direct invocation shape:

```kotlin
val result = io.github.qingshu.mcpaudiotools.mcptool.subTitleToLrc(
    inputPath = inputPath,
    outputPath = outputPath,
)
```

Confirm the schema/request keys remain snake_case:

```kotlin
putJsonObject("input_path")
val inputPathPresent = arguments?.containsKey("input_path") == true
val inputPath = arguments?.get("input_path")?.jsonPrimitive?.contentOrNull
```

Confirm no direct-call unnecessary assertions remain for the reported subtitle case:

```text
inputPath = inputPath!!
outputPath = outputPath!!
```

Expected: the two `!!` strings above are absent from the generated subtitle direct call.

---

## Self-Review

- Spec coverage: The plan covers all three requested issues: indentation, schema alias/Kotlin naming separation, and unnecessary `!!` after same-scope smart casts. It also covers the requested `mcp-audio-tools` migration.
- Placeholder scan: No `TBD`, `TODO`, vague edge-case instructions, or undefined implementation references remain.
- Type consistency: `ToolParameter.schemaName`, `ToolParam.name`, and `NonNullAssertionMode` are introduced before use. Helper invocations keep required `!!` because helper parameters remain nullable across the function boundary, while direct invocations omit `!!` after generated guards.
