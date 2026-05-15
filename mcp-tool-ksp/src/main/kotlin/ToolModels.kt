package io.github.qingshu.mcptool.ksp

import com.squareup.kotlinpoet.ClassName

data class ToolFunction(
    val packageName: String,
    val functionName: String,
    val toolName: String,
    val description: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val contextParameters: List<ContextParameter> = emptyList(),
    val returnType: ToolReturnType,
)

data class ToolParameter(
    val name: String,
    val schemaName: String,
    val description: String,
    val type: ParameterType,
    val nullable: Boolean,
    val hasDefault: Boolean,
    val required: Boolean,
)

sealed class ParameterType(
    val kotlinType: ClassName,
    val jsonSchemaType: String,
) {
    data object StringType : ParameterType(ClassName("kotlin", "String"), "string")

    data object IntType : ParameterType(ClassName("kotlin", "Int"), "integer")

    data object LongType : ParameterType(ClassName("kotlin", "Long"), "integer")

    data object DoubleType : ParameterType(ClassName("kotlin", "Double"), "number")

    data object BooleanType : ParameterType(ClassName("kotlin", "Boolean"), "boolean")

    companion object {
        fun fromQualifiedName(qualifiedName: String): ParameterType? = when (qualifiedName) {
            "kotlin.String" -> StringType
            "kotlin.Int" -> IntType
            "kotlin.Long" -> LongType
            "kotlin.Double" -> DoubleType
            "kotlin.Boolean" -> BooleanType
            else -> null
        }
    }
}

data class ContextParameter(
    val name: String,
    val type: ContextParameterType,
)

enum class ContextParameterType {
    CallToolRequest,
    ReadResourceRequest,
    GetPromptRequest,
    ClientConnection,
    Server,
    ;

    companion object {
        private val QUALIFIED_NAMES: Map<String, ContextParameterType> = mapOf(
            "io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest" to CallToolRequest,
            "io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest" to ReadResourceRequest,
            "io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest" to GetPromptRequest,
            "io.modelcontextprotocol.kotlin.sdk.server.ClientConnection" to ClientConnection,
            "io.modelcontextprotocol.kotlin.sdk.server.Server" to Server,
        )

        fun fromQualifiedName(qualifiedName: String): ContextParameterType? = QUALIFIED_NAMES[qualifiedName]
    }
}

sealed class ToolReturnType {
    data object UnitType : ToolReturnType()

    data object TextType : ToolReturnType()

    data object PrimitiveType : ToolReturnType()

    data object CallToolResultType : ToolReturnType()
}

data class ResourceFunction(
    val packageName: String,
    val functionName: String,
    val resourceName: String,
    val description: String,
    val location: ResourceLocation,
    val mimeType: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val contextParameters: List<ContextParameter> = emptyList(),
    val returnType: ResourceReturnType,
)

data class PromptFunction(
    val packageName: String,
    val functionName: String,
    val promptName: String,
    val description: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val contextParameters: List<ContextParameter> = emptyList(),
    val returnType: PromptReturnType,
)

sealed class ResourceLocation {
    data class Static(val uri: String) : ResourceLocation()
    data class Template(val uriTemplate: String) : ResourceLocation()

    companion object {
        fun from(uri: String, uriTemplate: String): ResourceLocation? = when {
            uri.isNotBlank() && uriTemplate.isBlank() -> Static(uri)
            uri.isBlank() && uriTemplate.isNotBlank() -> Template(uriTemplate)
            else -> null
        }
    }
}

sealed class ResourceReturnType {
    data object TextType : ResourceReturnType()
    data object TextResourceContentsType : ResourceReturnType()
    data object BlobResourceContentsType : ResourceReturnType()
    data object ReadResourceResultType : ResourceReturnType()
}

sealed class PromptReturnType {
    data object TextType : PromptReturnType()
    data object PromptMessageType : PromptReturnType()
    data object PromptMessageListType : PromptReturnType()
    data object GetPromptResultType : PromptReturnType()
}

fun extractUriTemplateVariables(uriTemplate: String): List<String> {
    val matches = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)}").findAll(uriTemplate)
    return matches.map { it.groupValues[1] }.distinct().toList()
}
