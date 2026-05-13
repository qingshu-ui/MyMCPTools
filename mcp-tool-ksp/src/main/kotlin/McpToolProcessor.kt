package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate

private const val MCP_TOOL_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpTool"
private const val MCP_RESOURCE_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpResource"
private const val MCP_PROMPT_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpPrompt"

internal class McpToolProcessor(
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
) : SymbolProcessor {
    private val context = ProcessorContext(codeGenerator, logger)
    private val seenTools = linkedMapOf<String, ToolDeclaration>()
    private val seenResources = linkedMapOf<String, ResourceDeclaration>()
    private val seenPrompts = linkedMapOf<String, PromptDeclaration>()
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val toolSymbols = resolver
            .getSymbolsWithAnnotation(MCP_TOOL_ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()
        val resourceSymbols = resolver
            .getSymbolsWithAnnotation(MCP_RESOURCE_ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()
        val promptSymbols = resolver
            .getSymbolsWithAnnotation(MCP_PROMPT_ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        val symbols = toolSymbols + resourceSymbols + promptSymbols

        val deferredSymbols = symbols.filterNot { it.validate() }
        val validToolSymbols = toolSymbols.filter { it.validate() }
        val validResourceSymbols = resourceSymbols.filter { it.validate() }
        val validPromptSymbols = promptSymbols.filter { it.validate() }
        var hasValidationErrors = false

        validToolSymbols.forEach { declaration ->
            val toolFunction = declaration.toToolFunctionOrNull(context.logger)
            if (toolFunction == null) {
                hasValidationErrors = true
            } else {
                seenTools[declaration.toolIdentity()] = ToolDeclaration(declaration, toolFunction)
            }
        }

        validResourceSymbols.forEach { declaration ->
            val resourceFunction = declaration.toResourceFunctionOrNull(context.logger)
            if (resourceFunction == null) {
                hasValidationErrors = true
            } else {
                seenResources[declaration.toolIdentity()] = ResourceDeclaration(declaration, resourceFunction)
            }
        }

        validPromptSymbols.forEach { declaration ->
            val promptFunction = declaration.toPromptFunctionOrNull(context.logger)
            if (promptFunction == null) {
                hasValidationErrors = true
            } else {
                seenPrompts[declaration.toolIdentity()] = PromptDeclaration(declaration, promptFunction)
            }
        }

        if (deferredSymbols.isNotEmpty()) return deferredSymbols
        if (hasValidationErrors) return emptyList()

        val duplicateToolNames = seenTools.values
            .groupBy { it.tool.toolName }
            .filterValues { it.size > 1 }

        duplicateToolNames.forEach { (name, declarations) ->
            declarations.forEach { toolDeclaration ->
                context.logger.error(
                    "Duplicate MCP tool name '$name'. Tool names must be unique.",
                    toolDeclaration.declaration,
                )
            }
        }

        val duplicateResourceNames = seenResources.values
            .groupBy { it.resource.resourceName }
            .filterValues { it.size > 1 }

        duplicateResourceNames.forEach { (name, declarations) ->
            declarations.forEach { resourceDeclaration ->
                context.logger.error(
                    "Duplicate MCP resource name '$name'. Resource names must be unique.",
                    resourceDeclaration.declaration,
                )
            }
        }

        val duplicateResourceLocations = seenResources.values
            .groupBy { it.resource.location }
            .filterValues { it.size > 1 }

        duplicateResourceLocations.forEach { (location, declarations) ->
            declarations.forEach { resourceDeclaration ->
                context.logger.error(
                    "Duplicate MCP resource location '$location'. Resource URIs and URI templates must be unique.",
                    resourceDeclaration.declaration,
                )
            }
        }

        val duplicatePromptNames = seenPrompts.values
            .groupBy { it.prompt.promptName }
            .filterValues { it.size > 1 }

        duplicatePromptNames.forEach { (name, declarations) ->
            declarations.forEach { promptDeclaration ->
                context.logger.error(
                    "Duplicate MCP prompt name '$name'. Prompt names must be unique.",
                    promptDeclaration.declaration,
                )
            }
        }

        val hasDuplicates = duplicateToolNames.isNotEmpty() ||
            duplicateResourceNames.isNotEmpty() ||
            duplicateResourceLocations.isNotEmpty() ||
            duplicatePromptNames.isNotEmpty()

        val hasDeclarations = seenTools.isNotEmpty() || seenResources.isNotEmpty() || seenPrompts.isNotEmpty()

        if (hasDeclarations && !hasDuplicates) {
            ToolCodeGenerator(context).generate(
                tools = seenTools.values.map { it.tool },
                resources = seenResources.values.map { it.resource },
                prompts = seenPrompts.values.map { it.prompt },
            )
            generated = true
        }

        return emptyList()
    }
}

private data class ToolDeclaration(
    val declaration: KSFunctionDeclaration,
    val tool: ToolFunction,
)

private data class ResourceDeclaration(
    val declaration: KSFunctionDeclaration,
    val resource: ResourceFunction,
)

private data class PromptDeclaration(
    val declaration: KSFunctionDeclaration,
    val prompt: PromptFunction,
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
