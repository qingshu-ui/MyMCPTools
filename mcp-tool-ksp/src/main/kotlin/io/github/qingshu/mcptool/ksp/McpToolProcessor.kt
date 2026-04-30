package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate

private const val MCP_TOOL_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpTool"

internal class McpToolProcessor(
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
) : SymbolProcessor {
    private val context = ProcessorContext(codeGenerator, logger)
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation(MCP_TOOL_ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        val deferredSymbols = symbols.filterNot { it.validate() }
        val validSymbols = symbols.filter { it.validate() }
        val toolFunctions = validSymbols.mapNotNull { declaration ->
            declaration.toToolFunctionOrNull(context.logger)?.let { toolFunction ->
                ToolDeclaration(declaration, toolFunction)
            }
        }

        val duplicateNames = toolFunctions
            .groupBy { it.tool.toolName }
            .filterValues { it.size > 1 }

        duplicateNames.forEach { (name, declarations) ->
            declarations.forEach { toolDeclaration ->
                context.logger.error(
                    "Duplicate MCP tool name '$name'. Tool names must be unique.",
                    toolDeclaration.declaration,
                )
            }
        }

        if (!generated && toolFunctions.isNotEmpty() && duplicateNames.isEmpty()) {
            ToolCodeGenerator(context).generate(toolFunctions.map { it.tool })
            generated = true
        }

        return deferredSymbols
    }
}

private data class ToolDeclaration(
    val declaration: KSFunctionDeclaration,
    val tool: ToolFunction,
)
