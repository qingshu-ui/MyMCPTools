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
import kotlin.text.replaceFirstChar

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
        fun render(tools: List<ToolFunction>): String = buildFileSpec(tools.sortedBy { it.toolName }).toString()

        private fun buildFileSpec(tools: List<ToolFunction>): FileSpec {
            val builder = FileSpec.builder(GENERATED_PACKAGE, GENERATED_FILE_NAME)
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
                .addFunction(buildAggregateFunction(tools))

            tools.forEach { tool ->
                builder.addFunction(buildRegistrationFunction(tool))
                if (tool.parameters.any(ToolParameter::hasDefault)) {
                    builder.addFunction(buildInvocationHelper(tool))
                }
            }

            return builder.build()
        }

        private fun buildAggregateFunction(tools: List<ToolFunction>): FunSpec = FunSpec.builder("registerGeneratedMcpTools")
            .receiver(serverType)
            .apply {
                tools.forEach { tool ->
                    addStatement("%N()", registrationFunctionName(tool))
                }
            }
            .build()

        private fun buildRegistrationFunction(tool: ToolFunction): FunSpec = FunSpec.builder(registrationFunctionName(tool))
            .receiver(serverType)
            .addCode(buildAddToolBlock(tool))
            .build()

        private fun buildAddToolBlock(tool: ToolFunction): CodeBlock {
            val code = CodeBlock.builder()
            code.add("addTool(\n")
            code.add("    name = %S,\n", tool.toolName)
            code.add("    description = %S,\n", tool.description)
            code.add("    inputSchema = %L,\n", buildInputSchema(tool.parameters))
            code.add(") { request ->\n")
            code.add("    try {\n")
            code.add("        val arguments = request.params.arguments\n")

            tool.parameters.forEach { parameter ->
                code.add("        val %NPresent = arguments?.containsKey(%S) == true\n", parameter.name, parameter.name)
                code.add("        val %N = arguments[%S]?.jsonPrimitive?.%L\n", parameter.name, parameter.name, parameter.type.accessorName())
            }

            tool.parameters.forEach { parameter ->
                if (!parameter.nullable) {
                    code.beginControlFlow("        if (%NPresent && %N == null)", parameter.name, parameter.name)
                    code.addStatement("return@addTool invalidArgumentResult(%S)", parameter.name)
                    code.endControlFlow()
                }
                if (parameter.required) {
                    code.beginControlFlow("        if (%N == null)", parameter.name)
                    code.addStatement("return@addTool missingRequiredArgumentResult(%S)", parameter.name)
                    code.endControlFlow()
                }
            }

            if (tool.parameters.any(ToolParameter::hasDefault)) {
                code.add("        val result = %N(\n", invocationHelperName(tool))
                tool.parameters.forEach { parameter ->
                    code.add("            %N = %N,\n", parameter.name, parameter.name)
                    if (parameter.hasDefault) {
                        code.add("            %NPresent = %NPresent,\n", parameter.name, parameter.name)
                    }
                }
                code.add("        )\n")
            } else {
                code.add("        val result = %L\n", buildInvocation(tool, tool.parameters.associate { it.name to true }))
            }

            code.add(buildResultHandling(tool))
            code.add("    } catch (exception: Exception) {\n")
            code.add("        return@addTool CallToolResult(\n")
            code.add("            content = listOf(TextContent(exception.message ?: %S)),\n", "Tool failed")
            code.add("            isError = true,\n")
            code.add("        )\n")
            code.add("    }\n")
            code.add("}\n")
            return code.build()
        }

        private fun buildInputSchema(parameters: List<ToolParameter>): CodeBlock {
            val requiredParameters = parameters.filter(ToolParameter::required)
            val code = CodeBlock.builder()
            code.add("ToolSchema(\n")
            code.add("        properties = buildJsonObject {\n")
            parameters.forEach { parameter ->
                code.add("            putJsonObject(%S) {\n", parameter.name)
                code.add("                put(%S, %S)\n", "type", parameter.type.jsonSchemaType)
                code.add("                put(%S, %S)\n", "description", parameter.description)
                code.add("            }\n")
            }
            code.add("        },\n")
            if (requiredParameters.isEmpty()) {
                code.add("        required = emptyList(),\n")
            } else {
                code.add(
                    "        required = listOf(%L),\n",
                    requiredParameters.joinToString(", ") { "\"${it.name}\"" },
                )
            }
            code.add("    )")
            return code.build()
        }

        private fun buildInvocationHelper(tool: ToolFunction): FunSpec {
            val builder = FunSpec.builder(invocationHelperName(tool))
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
                code.addStatement("return %L", buildInvocation(tool, includedDefaults))
                return
            }

            code.beginControlFlow("if (%NPresent)", next.name)
            buildInvocationBranches(code, tool, defaultParameters.drop(1), includedDefaults + (next.name to true))
            code.nextControlFlow("else")
            buildInvocationBranches(code, tool, defaultParameters.drop(1), includedDefaults + (next.name to false))
            code.endControlFlow()
        }

        private fun buildInvocation(tool: ToolFunction, includedDefaults: Map<String, Boolean>): CodeBlock {
            val code = CodeBlock.builder()
            code.add("%L.%L(\n", tool.packageName, tool.functionName)
            tool.parameters.forEach { parameter ->
                if (parameter.hasDefault && includedDefaults[parameter.name] == false) return@forEach
                code.add("    %N = %L,\n", parameter.name, buildInvocationArgument(parameter))
            }
            code.add(")")
            return code.build()
        }

        private fun buildInvocationArgument(parameter: ToolParameter): CodeBlock = if (parameter.nullable) {
            CodeBlock.of("%N", parameter.name)
        } else {
            CodeBlock.of("%N!!", parameter.name)
        }

        private fun buildResultHandling(tool: ToolFunction): CodeBlock {
            val code = CodeBlock.builder()
            when (tool.returnType) {
                ToolReturnType.TextType -> {
                    code.add("        return@addTool CallToolResult(\n")
                    code.add("            content = listOf(TextContent(result)),\n")
                    code.add("            isError = false,\n")
                    code.add("        )\n")
                }

                ToolReturnType.PrimitiveType -> {
                    code.add("        return@addTool CallToolResult(\n")
                    code.add("            content = listOf(TextContent(result.toString())),\n")
                    code.add("            isError = false,\n")
                    code.add("        )\n")
                }

                ToolReturnType.UnitType -> {
                    code.add("        return@addTool CallToolResult(\n")
                    code.add("            content = listOf(TextContent(%S)),\n", "[OK]")
                    code.add("            isError = false,\n")
                    code.add("        )\n")
                }

                ToolReturnType.CallToolResultType -> code.add("        return@addTool result\n")
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

        private fun registrationFunctionName(tool: ToolFunction): String = "register${tool.toolName.toPascalCase()}Tool"

        private fun invocationHelperName(tool: ToolFunction): String = "invoke${tool.toolName.toPascalCase()}Tool"

        private fun String.toPascalCase(): String = split('_', '-', '.', ' ')
            .filter { it.isNotBlank() }
            .joinToString(separator = "") { part -> part.replaceFirstChar { char -> char.uppercase() } }

        private fun missingRequiredArgumentResult(name: String): CallToolResult = CallToolResult(
            content = listOf(io.modelcontextprotocol.kotlin.sdk.types.TextContent("Missing required argument: $name")),
            isError = true,
        )

        private fun invalidArgumentResult(name: String): CallToolResult = CallToolResult(
            content = listOf(io.modelcontextprotocol.kotlin.sdk.types.TextContent("Invalid argument: $name")),
            isError = true,
        )
    }
}
