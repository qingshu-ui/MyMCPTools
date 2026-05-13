package io.github.qingshu.mcptool.annotations

/**
 * Documents an argument exposed in a generated MCP prompt schema.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class PromptParam(
    public val description: String,
    public val required: Required = Required.UNSPECIFIED,
    public val name: String = "",
)
