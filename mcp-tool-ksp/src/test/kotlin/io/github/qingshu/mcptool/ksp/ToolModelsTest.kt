package io.github.qingshu.mcptool.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolModelsTest {
    @Test
    fun `maps supported Kotlin types to JSON schema types`() {
        assertEquals(ParameterType.StringType, ParameterType.fromQualifiedName("kotlin.String"))
        assertEquals(ParameterType.IntType, ParameterType.fromQualifiedName("kotlin.Int"))
        assertEquals(ParameterType.LongType, ParameterType.fromQualifiedName("kotlin.Long"))
        assertEquals(ParameterType.DoubleType, ParameterType.fromQualifiedName("kotlin.Double"))
        assertEquals(ParameterType.BooleanType, ParameterType.fromQualifiedName("kotlin.Boolean"))
    }

    @Test
    fun `returns null for unsupported Kotlin types`() {
        assertNull(ParameterType.fromQualifiedName("kotlin.collections.List"))
        assertNull(ParameterType.fromQualifiedName("com.example.Custom"))
    }

    @Test
    fun `maps parameter types to JSON schema names`() {
        assertEquals("string", ParameterType.StringType.jsonSchemaType)
        assertEquals("integer", ParameterType.IntType.jsonSchemaType)
        assertEquals("integer", ParameterType.LongType.jsonSchemaType)
        assertEquals("number", ParameterType.DoubleType.jsonSchemaType)
        assertEquals("boolean", ParameterType.BooleanType.jsonSchemaType)
    }

    @Test
    fun `generates aggregate and per tool registration functions`() {
        val generated = ToolCodeGenerator.render(
            listOf(
                ToolFunction(
                    packageName = "com.example.tools",
                    functionName = "greet",
                    toolName = "greet_user",
                    description = "Greet a user by name.",
                    isSuspend = false,
                    parameters = listOf(
                        ToolParameter(
                            name = "name",
                            description = "Name to greet",
                            type = ParameterType.StringType,
                            nullable = false,
                            hasDefault = false,
                            required = true,
                        ),
                        ToolParameter(
                            name = "count",
                            description = "How many greetings to generate",
                            type = ParameterType.IntType,
                            nullable = false,
                            hasDefault = true,
                            required = false,
                        ),
                        ToolParameter(
                            name = "excited",
                            description = "Whether to add emphasis",
                            type = ParameterType.BooleanType,
                            nullable = true,
                            hasDefault = false,
                            required = false,
                        ),
                    ),
                    returnType = ToolReturnType.TextType,
                ),
            ),
        )

        assertTrue(generated.contains("package io.github.qingshu.mcptool.generated"))
        assertTrue(generated.contains("public fun Server.registerGeneratedMcpTools()"))
        assertTrue(generated.contains("registerGreetUserTool()"))
        assertTrue(generated.contains("public fun Server.registerGreetUserTool()"))
        assertTrue(generated.contains("name = \"greet_user\""))
        assertTrue(generated.contains("description = \"Greet a user by name.\""))
        assertTrue(generated.contains("put(\"type\", \"string\")"))
        assertTrue(generated.contains("put(\"type\", \"integer\")"))
        assertTrue(generated.contains("required = listOf(\"name\")"))
        assertTrue(generated.contains("val name = arguments[\"name\"]?.jsonPrimitive?.contentOrNull"))
        assertTrue(generated.contains("val count = arguments[\"count\"]?.jsonPrimitive?.intOrNull"))
        assertTrue(generated.contains("val excited = arguments[\"excited\"]?.jsonPrimitive?.booleanOrNull"))
        assertTrue(generated.contains("val result = invokeGreetUserTool("))
        assertTrue(generated.contains("return com.example.tools.greet("))
        assertTrue(generated.contains("count = count!!"))
        assertTrue(generated.contains("excited = excited"))
        assertTrue(generated.contains("TextContent(result)"))
        assertTrue(generated.contains("exception.message ?: \"Tool failed\""))
    }

    @Test
    fun `generates wrappers for unit and call tool result return types`() {
        val generated = ToolCodeGenerator.render(
            listOf(
                ToolFunction(
                    packageName = "com.example.tools",
                    functionName = "cleanup",
                    toolName = "cleanup",
                    description = "Perform cleanup.",
                    isSuspend = true,
                    parameters = emptyList(),
                    returnType = ToolReturnType.UnitType,
                ),
                ToolFunction(
                    packageName = "com.example.tools",
                    functionName = "passthrough",
                    toolName = "passthrough",
                    description = "Return MCP result.",
                    isSuspend = false,
                    parameters = listOf(
                        ToolParameter(
                            name = "id",
                            description = "Identifier",
                            type = ParameterType.LongType,
                            nullable = false,
                            hasDefault = false,
                            required = true,
                        ),
                    ),
                    returnType = ToolReturnType.CallToolResultType,
                ),
            ),
        )

        assertTrue(generated.contains("registerCleanupTool()"))
        assertTrue(generated.contains("registerPassthroughTool()"))
        assertTrue(generated.contains("val result = com.example.tools.cleanup("))
        assertTrue(generated.contains("TextContent(\"[OK]\")"))
        assertTrue(generated.contains("val result = com.example.tools.passthrough("))
        assertTrue(generated.contains("return@addTool result"))
        assertTrue(generated.contains("arguments[\"id\"]?.jsonPrimitive?.longOrNull"))
    }
}
