package io.github.qingshu.mcptool.annotations

/**
 * Marks a top-level function as an MCP resource definition.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class McpResource(
    public val name: String,
    public val description: String,
    public val uri: String = "",
    public val uriTemplate: String = "",
    public val mimeType: String = "text/plain",
)
