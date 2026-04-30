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
    private val seenTools = linkedMapOf<String, ToolDeclaration>()
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val symbols = resolver
            .getSymbolsWithAnnotation(MCP_TOOL_ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        val deferredSymbols = symbols.filterNot { it.validate() }
        val validSymbols = symbols.filter { it.validate() }
        var hasValidationErrors = false

        validSymbols.forEach { declaration ->
            val toolFunction = declaration.toToolFunctionOrNull(context.logger)
            if (toolFunction == null) {
                hasValidationErrors = true
            } else {
                seenTools[declaration.toolIdentity()] = ToolDeclaration(declaration, toolFunction)
            }
        }

        if (deferredSymbols.isNotEmpty()) return deferredSymbols
        if (hasValidationErrors) return emptyList()

        val duplicateNames = seenTools.values
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

        if (seenTools.isNotEmpty() && duplicateNames.isEmpty()) {
            ToolCodeGenerator(context).generate(seenTools.values.map { it.tool })
            generated = true
        }

        return emptyList()
    }
}

private data class ToolDeclaration(
    val declaration: KSFunctionDeclaration,
    val tool: ToolFunction,
)

private fun KSFunctionDeclaration.toolIdentity(): String = buildString {
    append(packageName.asString())
    append('.')
    append(simpleName.asString())
    append('(')
    parameters.joinTo(this, separator = ",") { parameter ->
        parameter.type.resolve().declaration.qualifiedName?.asString().orEmpty()
    }
    append(')')
}
