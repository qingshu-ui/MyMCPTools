package io.github.qingshu.mcptool.annotations

/**
 * Tri-state requiredness for tool parameters.
 *
 * UNSPECIFIED lets the processor infer requiredness from Kotlin nullability and defaults.
 */
public enum class Required {
    UNSPECIFIED,
    TRUE,
    FALSE,
}

/**
 * Documents a parameter exposed in a generated MCP input schema.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class ToolParam(
    public val description: String,
    public val required: Required = Required.UNSPECIFIED,
)
