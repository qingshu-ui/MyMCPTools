package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import io.github.qingshu.mcptool.annotations.Required
import kotlin.text.replaceFirstChar

internal sealed class ParameterResolution {
    data class Schema(val parameter: ToolParameter) : ParameterResolution()
    data class Context(val parameter: ContextParameter) : ParameterResolution()
}

internal data class SeparatedParameters(
    val schemaParameters: List<ToolParameter>,
    val contextParameters: List<ContextParameter>,
)

internal fun separateAndValidateParameters(
    resolutions: List<ParameterResolution>,
    annotationName: String,
    logger: KSPLogger,
    symbol: KSNode,
): SeparatedParameters? {
    val schemaParameters = resolutions.filterIsInstance<ParameterResolution.Schema>().map { it.parameter }
    val contextParameters = resolutions.filterIsInstance<ParameterResolution.Context>().map { it.parameter }
    val duplicateContextTypes = contextParameters.groupBy { it.type }.filterValues { it.size > 1 }.keys
    if (duplicateContextTypes.isNotEmpty()) {
        logger.error(
            "Duplicate context type(s) in $annotationName: ${duplicateContextTypes.joinToString()}",
            symbol,
        )
        return null
    }
    return SeparatedParameters(schemaParameters, contextParameters)
}

internal fun resolveContextParameter(
    parameterName: String,
    resolvedType: KSType,
    hasDefault: Boolean,
    validContextTypes: Set<ContextParameterType>,
    annotationName: String,
    logger: KSPLogger,
    symbol: KSNode,
): ParameterResolution.Context? {
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()
    val contextType = ContextParameterType.fromQualifiedName(qualifiedType)
    if (contextType == null) return null
    if (contextType !in validContextTypes) {
        logger.error(
            "Context type '${contextType.name}' is not supported for $annotationName. Supported: ${validContextTypes.joinToString { it.name }}.",
            symbol,
        )
        return null
    }
    if (resolvedType.isMarkedNullable) {
        logger.error("Context parameter '$parameterName' must not be nullable.", symbol)
        return null
    }
    if (hasDefault) {
        logger.error("Context parameter '$parameterName' must not have a default value.", symbol)
        return null
    }
    return ParameterResolution.Context(ContextParameter(name = parameterName, type = contextType))
}

private const val MCP_TOOL_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpTool"
private const val TOOL_PARAM_ANNOTATION = "io.github.qingshu.mcptool.annotations.ToolParam"
private const val CALL_TOOL_RESULT = "io.modelcontextprotocol.kotlin.sdk.types.CallToolResult"

private val VALID_TOOL_CONTEXT_TYPES: Set<ContextParameterType> = setOf(
    ContextParameterType.CallToolRequest,
    ContextParameterType.ClientConnection,
    ContextParameterType.Server,
)

internal fun inferRequiredness(
    nullable: Boolean,
    hasDefault: Boolean,
    explicit: Required,
): Boolean = when (explicit) {
    Required.UNSPECIFIED -> !nullable && !hasDefault

    Required.FALSE -> false

    Required.TRUE -> {
        require(!nullable && !hasDefault) {
            "Parameter cannot be required when its Kotlin type is nullable or has a default value."
        }
        true
    }
}

internal fun String.normalizedToolFunctionNameComponent(): String = split('_', '-', '.', ' ')
    .filter { it.isNotBlank() }
    .joinToString(separator = "") { part -> part.replaceFirstChar { char -> char.uppercase() } }

internal fun duplicateSchemaNames(parameters: List<ToolParameter>): Set<String> = parameters
    .groupingBy { it.schemaName }
    .eachCount()
    .filterValues { it > 1 }
    .keys

internal fun resolveSchemaName(annotationName: String?, parameterName: String): String = annotationName.orEmpty().ifBlank {
    parameterName
}

internal fun validateUniqueSchemaNames(
    parameters: List<ToolParameter>,
    toolName: String,
    logger: KSPLogger,
    symbol: KSFunctionDeclaration,
): Boolean {
    val duplicates = duplicateSchemaNames(parameters)
    if (duplicates.isEmpty()) return true
    logger.error(
        "Duplicate @ToolParam schema name(s) for tool '$toolName': ${duplicates.joinToString()}",
        symbol,
    )
    return false
}

internal fun KSFunctionDeclaration.toToolFunctionOrNull(logger: KSPLogger): ToolFunction? {
    val toolAnnotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == MCP_TOOL_ANNOTATION
    } ?: return null

    if (parentDeclaration != null) {
        logger.error("@McpTool is only supported on top-level functions in v1.", this)
        return null
    }

    val toolName = toolAnnotation.argumentValue<String>("name").orEmpty()
    val description = toolAnnotation.argumentValue<String>("description").orEmpty()

    if (toolName.isBlank()) {
        logger.error("@McpTool name must not be blank.", this)
        return null
    }
    if (toolName.normalizedToolFunctionNameComponent().isBlank()) {
        logger.error(
            "@McpTool name must contain at least one letter or digit usable in generated Kotlin function names.",
            this,
        )
        return null
    }
    if (description.isBlank()) {
        logger.error("@McpTool description must not be blank.", this)
        return null
    }

    val resolutions = parameters.map { parameter ->
        parameter.resolveToolParameter(logger) ?: return null
    }

    val separated = separateAndValidateParameters(resolutions, "@McpTool", logger, this) ?: return null

    if (!validateUniqueSchemaNames(separated.schemaParameters, toolName, logger, this)) return null

    val returnType = resolveReturnType(logger) ?: return null

    return ToolFunction(
        packageName = packageName.asString(),
        functionName = simpleName.asString(),
        toolName = toolName,
        description = description,
        isSuspend = modifiers.contains(Modifier.SUSPEND),
        parameters = separated.schemaParameters,
        contextParameters = separated.contextParameters,
        returnType = returnType,
    )
}

internal fun KSValueParameter.resolveToolParameter(
    logger: KSPLogger,
): ParameterResolution? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpTool parameters must have stable names.", this)
        return null
    }

    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == TOOL_PARAM_ANNOTATION
    }

    if (annotation != null) {
        return resolveSchemaParameter(annotation, parameterName, logger)
    }

    val contextResult = resolveContextParameter(
        parameterName = parameterName,
        resolvedType = type.resolve(),
        hasDefault = hasDefault,
        validContextTypes = VALID_TOOL_CONTEXT_TYPES,
        annotationName = "@McpTool",
        logger = logger,
        symbol = this,
    )
    if (contextResult != null) return contextResult

    logger.error(
        "Parameter '$parameterName' must be annotated with @ToolParam or be a known context type " +
            "(${VALID_TOOL_CONTEXT_TYPES.joinToString { it.name }}).",
        this,
    )
    return null
}

private fun KSValueParameter.resolveSchemaParameter(
    annotation: KSAnnotation,
    parameterName: String,
    logger: KSPLogger,
): ParameterResolution? {
    val description = annotation.argumentValue<String>("description").orEmpty()
    if (description.isBlank()) {
        logger.error("@ToolParam description for '$parameterName' must not be blank.", this)
        return null
    }

    val schemaName = resolveSchemaName(
        annotationName = annotation.argumentValue("name"),
        parameterName = parameterName,
    )

    val resolvedType = type.resolve()
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()
    val parameterType = ParameterType.fromQualifiedName(qualifiedType)
    if (parameterType == null) {
        logger.error(
            "Unsupported @ToolParam type '$qualifiedType' for '$parameterName'. Supported types: String, Int, Long, Double, Boolean.",
            this,
        )
        return null
    }

    val explicitRequired = annotation.requiredArgumentValue() ?: Required.UNSPECIFIED

    val required = try {
        inferRequiredness(
            nullable = resolvedType.isMarkedNullable,
            hasDefault = hasDefault,
            explicit = explicitRequired,
        )
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid requiredness for parameter '$parameterName': ${e.message}", this)
        return null
    }

    return ParameterResolution.Schema(
        ToolParameter(
            name = parameterName,
            schemaName = schemaName,
            description = description,
            type = parameterType,
            nullable = resolvedType.isMarkedNullable,
            hasDefault = hasDefault,
            required = required,
        ),
    )
}

private fun KSFunctionDeclaration.resolveReturnType(logger: KSPLogger): ToolReturnType? {
    val resolved = returnType?.resolve()
    val qualifiedName = resolved?.declaration?.qualifiedName?.asString() ?: "kotlin.Unit"
    return when (qualifiedName) {
        "kotlin.Unit" -> ToolReturnType.UnitType

        "kotlin.String" -> ToolReturnType.TextType

        "kotlin.Int", "kotlin.Long", "kotlin.Double", "kotlin.Boolean" -> ToolReturnType.PrimitiveType

        CALL_TOOL_RESULT -> ToolReturnType.CallToolResultType

        else -> {
            logger.error(
                "Unsupported @McpTool return type '$qualifiedName'. Supported returns: Unit, String, Int, Long, Double, Boolean, CallToolResult.",
                this,
            )
            null
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T> KSAnnotation.argumentValue(name: String): T? = arguments.firstOrNull { it.name?.asString() == name }?.value as? T

internal fun KSAnnotation.requiredArgumentValue(): Required? = arguments
    .firstOrNull { it.name?.asString() == "required" }
    ?.toRequiredEnumValue()

internal fun KSValueArgument.toRequiredEnumValue(): Required? {
    val value = value ?: return null
    return when (value) {
        is Required -> value
        is KSClassDeclaration -> Required.entries.firstOrNull { it.name == value.simpleName.asString() }
        else -> null
    }
}
