package io.github.qingshu.mcptool.annotations

/**
 * Marks a top-level function as an MCP prompt definition.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class McpPrompt(
    public val name: String,
    public val description: String,
)
