package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import java.io.OutputStreamWriter

private const val GENERATED_PACKAGE = "io.github.qingshu.mcptool.generated"
private const val GENERATED_FILE_NAME = "GeneratedMcpTools"

private val serverType = Server::class.asTypeName()
private val stringType = String::class.asTypeName()
private val booleanType = Boolean::class.asTypeName()
private val anyType = Any::class.asTypeName()
private val unitType = Unit::class.asTypeName()
private val callToolResultType = CallToolResult::class.asTypeName()

internal class ToolCodeGenerator(private val context: ProcessorContext) {
    fun generate(tools: List<ToolFunction>) {
        val rendered = render(tools)
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
        private enum class NonNullAssertionMode {
            SmartCasted,
            Required,
        }

        fun render(tools: List<ToolFunction>): String = buildFileSpec(tools.sortedBy { it.toolName }).toString()

        private fun buildFileSpec(tools: List<ToolFunction>): FileSpec {
            val generatedNames = GeneratedToolNames.create(tools)
            val builder = FileSpec.builder(GENERATED_PACKAGE, GENERATED_FILE_NAME)
                .indent("    ")
                .addImport("io.modelcontextprotocol.kotlin.sdk.types", "CallToolResult", "TextContent", "ToolSchema")
                .addImport(
                    "kotlinx.serialization.json",
                    "booleanOrNull",
                    "buildJsonObject",
                    "contentOrNull",
                    "doubleOrNull",
                    "intOrNull",
                    "jsonPrimitive",
                    "longOrNull",
                    "put",
                    "putJsonObject",
                )
                .addFunction(buildAggregateFunction(tools, generatedNames))
                .addFunction(buildMissingRequiredArgumentResultFunction())
                .addFunction(buildInvalidArgumentResultFunction())

            tools.forEach { tool ->
                builder.addFunction(buildRegistrationFunction(tool, generatedNames))
                if (tool.parameters.any(ToolParameter::hasDefault)) {
                    builder.addFunction(buildInvocationHelper(tool, generatedNames))
                }
            }

            return builder.build()
        }

        private fun buildAggregateFunction(tools: List<ToolFunction>, generatedNames: GeneratedToolNames): FunSpec = FunSpec.builder("registerGeneratedMcpTools")
            .receiver(serverType)
            .apply {
                tools.forEach { tool ->
                    addStatement("%N()", generatedNames.registrationFunctionName(tool))
                }
            }
            .build()

        private fun buildRegistrationFunction(tool: ToolFunction, generatedNames: GeneratedToolNames): FunSpec = FunSpec.builder(generatedNames.registrationFunctionName(tool))
            .receiver(serverType)
            .addCode(buildAddToolBlock(tool, generatedNames))
            .build()

        private fun buildAddToolBlock(tool: ToolFunction, generatedNames: GeneratedToolNames): CodeBlock {
            val code = CodeBlock.builder()
            code.add("addTool(\n")
            code.indent()
            code.addStatement("name = %S,", tool.toolName)
            code.addStatement("description = %S,", tool.description)
            code.add("inputSchema = %L,\n", buildInputSchema(tool.parameters))
            code.unindent()
            code.add(") { request ->\n")
            code.indent()
            code.beginControlFlow("try")
            code.addStatement("val arguments = request.params.arguments")

            tool.parameters.forEach { parameter ->
                code.addStatement("val %NPresent = arguments?.containsKey(%S) == true", parameter.name, parameter.schemaName)
                code.addStatement(
                    "val %N = arguments?.get(%S)?.jsonPrimitive?.%L",
                    parameter.name,
                    parameter.schemaName,
                    parameter.type.accessorName(),
                )
            }

            tool.parameters.forEach { parameter ->
                if (!parameter.nullable) {
                    code.beginControlFlow("if (%NPresent && %N == null)", parameter.name, parameter.name)
                    code.addStatement("return@addTool invalidArgumentResult(%S)", parameter.schemaName)
                    code.endControlFlow()
                }
                if (parameter.required) {
                    code.beginControlFlow("if (%N == null)", parameter.name)
                    code.addStatement("return@addTool missingRequiredArgumentResult(%S)", parameter.schemaName)
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
                code.add(
                    "val result = %L\n",
                    buildInvocation(
                        tool = tool,
                        includedDefaults = tool.parameters.associate { it.name to true },
                        nonNullAssertionMode = NonNullAssertionMode.SmartCasted,
                    ),
                )
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

        private fun buildInputSchema(parameters: List<ToolParameter>): CodeBlock {
            val requiredParameters = parameters.filter(ToolParameter::required)
            val code = CodeBlock.builder()
            code.add("ToolSchema(\n")
            code.indent()
            code.add("properties = buildJsonObject {\n")
            code.indent()
            parameters.forEach { parameter ->
                code.add("putJsonObject(%S) {\n", parameter.schemaName)
                code.indent()
                code.add("put(%S, %S)\n", "type", parameter.type.jsonSchemaType)
                code.add("put(%S, %S)\n", "description", parameter.description)
                code.unindent()
                code.add("}\n")
            }
            code.unindent()
            code.add("},\n")
            if (requiredParameters.isEmpty()) {
                code.add("required = emptyList(),\n")
            } else {
                code.add(
                    "required = listOf(%L),\n",
                    requiredParameters.joinToString(", ") { "\"${it.schemaName}\"" },
                )
            }
            code.unindent()
            code.add(")")
            return code.build()
        }

        private fun buildInvocationHelper(tool: ToolFunction, generatedNames: GeneratedToolNames): FunSpec {
            val builder = FunSpec.builder(generatedNames.invocationHelperName(tool))
                .addModifiers(KModifier.PRIVATE)
                .returns(tool.returnType.generatedReturnType())

            if (tool.isSuspend) {
                builder.addModifiers(KModifier.SUSPEND)
            }

            tool.parameters.forEach { parameter ->
                builder.addParameter(ParameterSpec.builder(parameter.name, parameter.generatedParameterType()).build())
                if (parameter.hasDefault) {
                    builder.addParameter(ParameterSpec.builder("${parameter.name}Present", booleanType).build())
                }
            }

            val code = CodeBlock.builder()
            code.add("return ")
            buildInvocationBranches(
                code = code,
                tool = tool,
                defaultParameters = tool.parameters.filter(ToolParameter::hasDefault),
                includedDefaults = emptyMap(),
            )
            builder.addCode(code.build())
            return builder.build()
        }

        private fun buildInvocationBranches(
            code: CodeBlock.Builder,
            tool: ToolFunction,
            defaultParameters: List<ToolParameter>,
            includedDefaults: Map<String, Boolean>,
        ) {
            val next = defaultParameters.firstOrNull()
            if (next == null) {
                code.add(
                    "%L\n",
                    buildInvocation(
                        tool = tool,
                        includedDefaults = includedDefaults,
                        nonNullAssertionMode = NonNullAssertionMode.Required,
                    ),
                )
                return
            }

            code.beginControlFlow("if (%NPresent)", next.name)
            buildInvocationBranches(code, tool, defaultParameters.drop(1), includedDefaults + (next.name to true))
            code.nextControlFlow("else")
            buildInvocationBranches(code, tool, defaultParameters.drop(1), includedDefaults + (next.name to false))
            code.endControlFlow()
        }

        private fun buildInvocation(
            tool: ToolFunction,
            includedDefaults: Map<String, Boolean>,
            nonNullAssertionMode: NonNullAssertionMode,
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
            code.add("⇤)")
            return code.build()
        }

        private fun buildInvocationArgument(
            parameter: ToolParameter,
            nonNullAssertionMode: NonNullAssertionMode,
        ): CodeBlock = when {
            parameter.nullable -> CodeBlock.of("%N", parameter.name)
            nonNullAssertionMode == NonNullAssertionMode.SmartCasted && parameter.required -> CodeBlock.of("%N", parameter.name)
            else -> CodeBlock.of("%N!!", parameter.name)
        }

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

        private fun ToolParameter.generatedParameterType(): TypeName = type.kotlinType.copy(nullable = true)

        private fun ToolReturnType.generatedReturnType(): TypeName = when (this) {
            ToolReturnType.TextType -> stringType
            ToolReturnType.PrimitiveType -> anyType
            ToolReturnType.UnitType -> unitType
            ToolReturnType.CallToolResultType -> callToolResultType
        }

        private fun ParameterType.accessorName(): String = when (this) {
            ParameterType.StringType -> "contentOrNull"
            ParameterType.IntType -> "intOrNull"
            ParameterType.LongType -> "longOrNull"
            ParameterType.DoubleType -> "doubleOrNull"
            ParameterType.BooleanType -> "booleanOrNull"
        }

        private class GeneratedToolNames private constructor(
            private val namesByTool: Map<ToolFunction, ToolNames>,
        ) {
            fun registrationFunctionName(tool: ToolFunction): String = namesByTool.getValue(tool).registration

            fun invocationHelperName(tool: ToolFunction): String = namesByTool.getValue(tool).invocation

            companion object {
                fun create(tools: List<ToolFunction>): GeneratedToolNames {
                    val baseNameCounts = tools.groupingBy { it.toolName.normalizedToolFunctionNameComponent() }.eachCount()
                    val indicesByBaseName = linkedMapOf<String, Int>()
                    val namesByTool = LinkedHashMap<ToolFunction, ToolNames>()
                    tools.forEach { tool ->
                        val baseName = tool.toolName.normalizedToolFunctionNameComponent()
                        val index = indicesByBaseName.compute(baseName) { _, count -> (count ?: 0) + 1 }!!
                        val uniqueName = if (baseNameCounts.getValue(baseName) == 1) baseName else "$baseName$index"
                        namesByTool[tool] = ToolNames(
                            registration = "register${uniqueName}Tool",
                            invocation = "invoke${uniqueName}Tool",
                        )
                    }
                    return GeneratedToolNames(namesByTool)
                }
            }
        }

        private data class ToolNames(
            val registration: String,
            val invocation: String,
        )
    }
}
