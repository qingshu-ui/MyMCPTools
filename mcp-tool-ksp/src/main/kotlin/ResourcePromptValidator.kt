package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier

private const val MCP_RESOURCE_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpResource"
private const val MCP_PROMPT_ANNOTATION = "io.github.qingshu.mcptool.annotations.McpPrompt"
private const val PROMPT_PARAM_ANNOTATION = "io.github.qingshu.mcptool.annotations.PromptParam"
private const val READ_RESOURCE_RESULT = "io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult"
private const val TEXT_RESOURCE_CONTENTS = "io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents"
private const val BLOB_RESOURCE_CONTENTS = "io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents"
private const val GET_PROMPT_RESULT = "io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult"
private const val PROMPT_MESSAGE = "io.modelcontextprotocol.kotlin.sdk.types.PromptMessage"
private const val PROMPT_MESSAGE_LIST = "kotlin.collections.List"

internal fun validateResourceLocation(uri: String, uriTemplate: String): String? = if (ResourceLocation.from(uri, uriTemplate) == null) "@McpResource must specify exactly one of uri or uriTemplate." else null

internal fun validateUriTemplateParameters(uriTemplate: String, parameterNames: Set<String>): String? {
    val missing = extractUriTemplateVariables(uriTemplate).filterNot(parameterNames::contains)
    return if (missing.isEmpty()) null else "URI template variable(s) missing matching function parameters: ${missing.joinToString()}"
}

internal fun validateStaticResourceParameters(parameterNames: List<String>): String? = if (parameterNames.isEmpty()) null else "Static @McpResource functions must not declare parameters."

internal fun KSFunctionDeclaration.toResourceFunctionOrNull(logger: KSPLogger): ResourceFunction? {
    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == MCP_RESOURCE_ANNOTATION
    } ?: return null

    if (parentDeclaration != null) {
        logger.error("@McpResource is only supported on top-level functions in v1.", this)
        return null
    }

    val name = annotation.argumentValue<String>("name").orEmpty()
    val description = annotation.argumentValue<String>("description").orEmpty()
    val uri = annotation.argumentValue<String>("uri").orEmpty()
    val uriTemplate = annotation.argumentValue<String>("uriTemplate").orEmpty()
    val mimeType = annotation.argumentValue<String>("mimeType").orEmpty().ifBlank { "text/plain" }
    val location = ResourceLocation.from(uri, uriTemplate)

    if (name.isBlank()) {
        logger.error("@McpResource name must not be blank.", this)
        return null
    }
    if (name.normalizedToolFunctionNameComponent().isBlank()) {
        logger.error(
            "@McpResource name must contain at least one letter or digit usable in generated Kotlin function names.",
            this,
        )
        return null
    }
    if (description.isBlank()) {
        logger.error("@McpResource description must not be blank.", this)
        return null
    }
    validateResourceLocation(uri, uriTemplate)?.let { message ->
        logger.error(message, this)
        return null
    }

    val resolutions = parameters.map { parameter -> parameter.resolveResourceParameter(logger) }
    if (resolutions.any { it == null }) return null

    val schemaParameters = resolutions.filterIsInstance<ParameterResolution.Schema>().map { it.parameter }
    val contextParameters = resolutions.filterIsInstance<ParameterResolution.Context>().map { it.parameter }

    val duplicateContextTypes = contextParameters.groupBy { it.type }.filterValues { it.size > 1 }.keys
    if (duplicateContextTypes.isNotEmpty()) {
        logger.error(
            "Duplicate context type(s) in @McpResource: ${duplicateContextTypes.joinToString()}",
            this,
        )
        return null
    }

    when (location) {
        is ResourceLocation.Static -> validateStaticResourceParameters(schemaParameters.map { it.name })

        is ResourceLocation.Template -> validateUriTemplateParameters(
            location.uriTemplate,
            schemaParameters.map { it.name }.toSet(),
        )

        null -> null
    }?.let { message ->
        logger.error(message, this)
        return null
    }

    val returnType = resolveResourceReturnType(logger) ?: return null

    return ResourceFunction(
        packageName = packageName.asString(),
        functionName = simpleName.asString(),
        resourceName = name,
        description = description,
        location = location!!,
        mimeType = mimeType,
        isSuspend = modifiers.contains(Modifier.SUSPEND),
        parameters = schemaParameters,
        contextParameters = contextParameters,
        returnType = returnType,
    )
}

internal fun KSFunctionDeclaration.toPromptFunctionOrNull(logger: KSPLogger): PromptFunction? {
    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == MCP_PROMPT_ANNOTATION
    } ?: return null

    if (parentDeclaration != null) {
        logger.error("@McpPrompt is only supported on top-level functions in v1.", this)
        return null
    }

    val name = annotation.argumentValue<String>("name").orEmpty()
    val description = annotation.argumentValue<String>("description").orEmpty()

    if (name.isBlank()) {
        logger.error("@McpPrompt name must not be blank.", this)
        return null
    }
    if (name.normalizedToolFunctionNameComponent().isBlank()) {
        logger.error(
            "@McpPrompt name must contain at least one letter or digit usable in generated Kotlin function names.",
            this,
        )
        return null
    }
    if (description.isBlank()) {
        logger.error("@McpPrompt description must not be blank.", this)
        return null
    }

    val resolutions = parameters.map { parameter -> parameter.resolvePromptParameter(logger) }
    if (resolutions.any { it == null }) return null

    val schemaParameters = resolutions.filterIsInstance<ParameterResolution.Schema>().map { it.parameter }
    val contextParameters = resolutions.filterIsInstance<ParameterResolution.Context>().map { it.parameter }

    val duplicateContextTypes = contextParameters.groupBy { it.type }.filterValues { it.size > 1 }.keys
    if (duplicateContextTypes.isNotEmpty()) {
        logger.error(
            "Duplicate context type(s) in @McpPrompt: ${duplicateContextTypes.joinToString()}",
            this,
        )
        return null
    }

    if (!validateUniqueSchemaNames(schemaParameters, name, logger, this)) return null

    val returnType = resolvePromptReturnType(logger) ?: return null

    return PromptFunction(
        packageName = packageName.asString(),
        functionName = simpleName.asString(),
        promptName = name,
        description = description,
        isSuspend = modifiers.contains(Modifier.SUSPEND),
        parameters = schemaParameters,
        contextParameters = contextParameters,
        returnType = returnType,
    )
}

private val VALID_RESOURCE_CONTEXT_TYPES: Set<ContextParameterType> = setOf(
    ContextParameterType.ReadResourceRequest,
    ContextParameterType.ClientConnection,
    ContextParameterType.Server,
)

private val VALID_PROMPT_CONTEXT_TYPES: Set<ContextParameterType> = setOf(
    ContextParameterType.GetPromptRequest,
    ContextParameterType.ClientConnection,
    ContextParameterType.Server,
)

private fun KSValueParameter.resolveResourceParameter(logger: KSPLogger): ParameterResolution? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpResource parameters must have stable names.", this)
        return null
    }

    val resolvedType = type.resolve()
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()

    // Check if it's a context parameter
    val contextType = ContextParameterType.fromQualifiedName(qualifiedType)
    if (contextType != null) {
        if (contextType !in VALID_RESOURCE_CONTEXT_TYPES) {
            logger.error(
                "Context type '${contextType.name}' is not supported for @McpResource. Supported: ReadResourceRequest, ClientConnection, Server.",
                this,
            )
            return null
        }
        if (resolvedType.isMarkedNullable) {
            logger.error("Context parameter '$parameterName' must not be nullable.", this)
            return null
        }
        if (hasDefault) {
            logger.error("Context parameter '$parameterName' must not have a default value.", this)
            return null
        }
        return ParameterResolution.Context(ContextParameter(name = parameterName, type = contextType))
    }

    // Fall through to URI template parameter logic
    val parameterType = ParameterType.fromQualifiedName(qualifiedType)
    if (parameterType != null) {
        return ParameterResolution.Schema(
            ToolParameter(
                name = parameterName,
                schemaName = parameterName,
                description = parameterName,
                type = parameterType,
                nullable = resolvedType.isMarkedNullable,
                hasDefault = hasDefault,
                required = inferRequiredness(
                    resolvedType.isMarkedNullable,
                    hasDefault,
                    io.github.qingshu.mcptool.annotations.Required.UNSPECIFIED,
                ),
            ),
        )
    }

    logger.error(
        "Unsupported parameter type '$qualifiedType' for '$parameterName'. Expected a URI template parameter type (String, Int, Long, Double, Boolean) or a context type (ReadResourceRequest, ClientConnection, Server).",
        this,
    )
    return null
}

private fun KSValueParameter.resolvePromptParameter(logger: KSPLogger): ParameterResolution? {
    val parameterName = name?.asString()
    if (parameterName.isNullOrBlank()) {
        logger.error("@McpPrompt parameters must have stable names.", this)
        return null
    }

    val annotation = annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == PROMPT_PARAM_ANNOTATION
    }

    if (annotation != null) {
        // Process as schema parameter
        val description = annotation.argumentValue<String>("description").orEmpty()
        if (description.isBlank()) {
            logger.error("@PromptParam description for '$parameterName' must not be blank.", this)
            return null
        }
        val schemaName = resolveSchemaName(annotation.argumentValue("name"), parameterName)
        val resolvedType = type.resolve()
        val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()
        val parameterType = ParameterType.fromQualifiedName(qualifiedType)
        if (parameterType == null) {
            logger.error(
                "Unsupported @PromptParam type '$qualifiedType' for '$parameterName'. Supported types: String, Int, Long, Double, Boolean.",
                this,
            )
            return null
        }
        val explicitRequired = annotation.requiredArgumentValue()
            ?: io.github.qingshu.mcptool.annotations.Required.UNSPECIFIED
        val required = try {
            inferRequiredness(resolvedType.isMarkedNullable, hasDefault, explicitRequired)
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

    // No @PromptParam annotation — check if it's a context parameter
    val resolvedType = type.resolve()
    val qualifiedType = resolvedType.declaration.qualifiedName?.asString().orEmpty()
    val contextType = ContextParameterType.fromQualifiedName(qualifiedType)

    if (contextType != null) {
        if (contextType !in VALID_PROMPT_CONTEXT_TYPES) {
            logger.error(
                "Context type '${contextType.name}' is not supported for @McpPrompt. Supported: GetPromptRequest, ClientConnection, Server.",
                this,
            )
            return null
        }
        if (resolvedType.isMarkedNullable) {
            logger.error("Context parameter '$parameterName' must not be nullable.", this)
            return null
        }
        if (hasDefault) {
            logger.error("Context parameter '$parameterName' must not have a default value.", this)
            return null
        }
        return ParameterResolution.Context(ContextParameter(name = parameterName, type = contextType))
    }

    logger.error(
        "Parameter '$parameterName' must be annotated with @PromptParam or be a recognized context type (GetPromptRequest, ClientConnection, Server).",
        this,
    )
    return null
}

private fun KSFunctionDeclaration.resolveResourceReturnType(logger: KSPLogger): ResourceReturnType? {
    val resolved = returnType?.resolve()
    val qualifiedName = resolved?.declaration?.qualifiedName?.asString() ?: "kotlin.Unit"
    return when (qualifiedName) {
        "kotlin.String" -> ResourceReturnType.TextType

        TEXT_RESOURCE_CONTENTS -> ResourceReturnType.TextResourceContentsType

        BLOB_RESOURCE_CONTENTS -> ResourceReturnType.BlobResourceContentsType

        READ_RESOURCE_RESULT -> ResourceReturnType.ReadResourceResultType

        else -> {
            logger.error(
                "Unsupported @McpResource return type '$qualifiedName'. Supported returns: String, TextResourceContents, BlobResourceContents, ReadResourceResult.",
                this,
            )
            null
        }
    }
}

private fun KSFunctionDeclaration.resolvePromptReturnType(logger: KSPLogger): PromptReturnType? {
    val resolved = returnType?.resolve()
    val qualifiedName = resolved?.declaration?.qualifiedName?.asString() ?: "kotlin.Unit"
    val typeArguments = resolved?.arguments.orEmpty()
    return when (qualifiedName) {
        "kotlin.String" -> PromptReturnType.TextType

        PROMPT_MESSAGE -> PromptReturnType.PromptMessageType

        GET_PROMPT_RESULT -> PromptReturnType.GetPromptResultType

        PROMPT_MESSAGE_LIST if typeArguments.firstOrNull()?.type?.resolve()?.declaration?.qualifiedName?.asString() == PROMPT_MESSAGE -> PromptReturnType.PromptMessageListType

        else -> {
            logger.error(
                "Unsupported @McpPrompt return type '$qualifiedName'. Supported returns: String, PromptMessage, List<PromptMessage>, GetPromptResult.",
                this,
            )
            null
        }
    }
}
