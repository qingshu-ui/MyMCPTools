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
