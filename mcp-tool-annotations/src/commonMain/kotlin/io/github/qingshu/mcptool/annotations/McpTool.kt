package io.github.qingshu.mcptool.annotations

/**
 * Marks a top-level function as an MCP tool definition.
 *
 * The KSP processor generates MCP SDK registration code for annotated functions.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class McpTool(
    public val name: String,
    public val description: String,
)
