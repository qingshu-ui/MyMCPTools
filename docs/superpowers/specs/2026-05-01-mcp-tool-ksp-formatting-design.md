# MCP Tool KSP Generated Formatting Design

Date: 2026-05-01

## Goal

`mcp-tool-ksp` should generate Kotlin source with standard, readable indentation directly from the generator. The generated code should not depend on a post-generation formatter such as Spotless, ktlint, or a custom string normalizer.

The current generator mixes KotlinPoet rendering with manual leading spaces inside `CodeBlock` strings. That makes nested structures such as `addTool`, `ToolSchema`, `try/catch`, and generated result blocks prone to missing or non-standard indentation. This design keeps the generated API and runtime behavior unchanged while making KotlinPoet responsible for indentation.

## Scope

The change is limited to `mcp-tool-ksp` generated-code formatting. It should focus on `ToolCodeGenerator` and the tests that verify rendered output.

In scope:

- Refactor indentation-sensitive `CodeBlock` construction in `ToolCodeGenerator`.
- Preserve generated package names, function names, imports, schemas, argument parsing, invocation semantics, and result handling behavior.
- Add formatting-sensitive tests that fail against the current bad formatting and pass when KotlinPoet owns indentation.
- Run the relevant Gradle tests for the KSP module.

Out of scope:

- Running Spotless, ktlint, or another formatter over generated source as part of generation.
- Changing public generated APIs such as `registerGeneratedMcpTools()` or per-tool registration function names.
- Reworking the generated architecture beyond what is necessary for stable formatting.
- Changing MCP tool validation rules or runtime behavior.

## Recommended Approach

Use KotlinPoet's structured APIs consistently so generated indentation comes from KotlinPoet rather than manually embedded spaces.

The generator should prefer:

- `addStatement(...)` for complete statements.
- `beginControlFlow(...)`, `nextControlFlow(...)`, and `endControlFlow()` for blocks.
- Nested `CodeBlock` values that are written from column zero and composed by KotlinPoet.

Manual leading spaces inside format strings should be removed from indentation-sensitive sections. Literal newlines are acceptable where KotlinPoet composition requires them, but they should not carry hard-coded nesting indentation.

## Components to Update

### `buildAddToolBlock`

This function currently emits the body of each generated `Server.addTool(...)` registration. It should be rebuilt so the outer `addTool` call, request lambda, `try/catch`, argument extraction, validation `if` blocks, invocation, and result handling all use structured KotlinPoet code generation.

Expected behavior remains the same:

1. Register the tool with the same name, description, and schema.
2. Read `request.params.arguments`.
3. Compute `<parameter>Present` flags.
4. Extract typed parameter values through the current JSON primitive accessors.
5. Return invalid-argument results for invalid non-null arguments.
6. Return missing-required-argument results for absent required arguments.
7. Invoke either the direct tool function or generated default-argument helper.
8. Convert the result to `CallToolResult`.
9. Catch exceptions and return an MCP error result.

### `buildInputSchema`

The `ToolSchema` block should be generated as a formatted nested expression. `properties = buildJsonObject { ... }`, each `putJsonObject(...)`, and each nested `put(...)` call should be indented by KotlinPoet.

The required list behavior remains unchanged:

- `required = emptyList()` when there are no required parameters.
- `required = listOf(...)` with the same names when required parameters exist.

### Result helper functions

`buildMissingRequiredArgumentResultFunction`, `buildInvalidArgumentResultFunction`, and `buildResultHandling` should emit `CallToolResult(...)` blocks without hard-coded leading indentation.

The generated text and `isError` values remain unchanged.

### Invocation helpers

Default-argument helper generation should continue to use recursive branches, but branch contents should avoid manually embedded indentation. If a helper block is difficult to format safely inline, small private `CodeBlock` helper functions may be added inside `ToolCodeGenerator` to centralize repeated shapes.

## Testing Strategy

Use TDD for the implementation.

First add a failing test in the existing KSP test suite that renders representative generated code and checks formatting-sensitive output. The test should assert enough exact substrings to catch the current indentation issue without becoming a full golden-file snapshot.

The representative rendered code should include:

- A tool with required, defaulted, and nullable parameters.
- An input schema with nested `buildJsonObject` and `putJsonObject` blocks.
- Argument extraction and validation branches.
- A generated invocation helper for a defaulted parameter.
- A `CallToolResult` success path.
- Exception handling.

Assertions should cover indentation around:

- `inputSchema = ToolSchema(`.
- `properties = buildJsonObject {`.
- nested `putJsonObject` and `put` calls.
- `try`, `catch`, validation `if`, and `return@addTool` blocks.
- result `CallToolResult(...)` blocks.

The test should also check that known bad patterns, such as over-indented schema properties caused by embedded leading spaces, are absent.

After the failing test is observed, refactor `ToolCodeGenerator` minimally until the test passes. Then run the relevant Gradle test task for `mcp-tool-ksp`, and run broader tests if the change affects shared generation behavior.

## Success Criteria

The work is complete when:

- The formatting-sensitive test fails before the generator refactor for the expected reason.
- The same test passes after the generator refactor.
- Existing `mcp-tool-ksp` tests still pass.
- Generated code structure and behavior remain compatible with existing assertions.
- No formatter or post-processing step is introduced.

## Risks and Mitigations

### Risk: brittle formatting tests

Exact generated-source tests can become too fragile if they assert the entire file. Mitigation: assert targeted formatting-sensitive blocks and bad-pattern absence instead of snapshotting the complete generated file.

### Risk: behavior accidentally changes while refactoring formatting

The generator emits executable adapter code, so formatting refactors can accidentally alter semantics. Mitigation: preserve the existing behavior assertions in `ToolModelsTest` and make changes incrementally.

### Risk: KotlinPoet composition remains awkward for deeply nested expressions

Some nested expressions may still require careful `CodeBlock` composition. Mitigation: introduce small private generator helpers for repeated expression shapes, but avoid a broad architecture rewrite.
