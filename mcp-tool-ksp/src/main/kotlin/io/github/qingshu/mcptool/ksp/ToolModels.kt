package io.github.qingshu.mcptool.ksp

import com.squareup.kotlinpoet.ClassName

data class ToolFunction(
    val packageName: String,
    val functionName: String,
    val toolName: String,
    val description: String,
    val isSuspend: Boolean,
    val parameters: List<ToolParameter>,
    val returnType: ToolReturnType,
)

data class ToolParameter(
    val name: String,
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

sealed class ToolReturnType {
    data object UnitType : ToolReturnType()

    data object TextType : ToolReturnType()

    data object PrimitiveType : ToolReturnType()

    data object CallToolResultType : ToolReturnType()
}
