package io.github.qingshu.mcptool.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolModelsTest {
    @Test
    fun `maps SDK context types to ContextParameterType`() {
        assertEquals(ContextParameterType.CallToolRequest, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest"))
        assertEquals(ContextParameterType.ReadResourceRequest, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest"))
        assertEquals(ContextParameterType.GetPromptRequest, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest"))
        assertEquals(ContextParameterType.ClientConnection, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.server.ClientConnection"))
        assertEquals(ContextParameterType.Server, ContextParameterType.fromQualifiedName("io.modelcontextprotocol.kotlin.sdk.server.Server"))
    }

    @Test
    fun `returns null for non-context qualified names`() {
        assertNull(ContextParameterType.fromQualifiedName("kotlin.String"))
        assertNull(ContextParameterType.fromQualifiedName("com.example.Custom"))
    }

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
        val generated = renderGreetTool()

        assertTrue(generated.contains("package io.github.qingshu.mcptool.generated"))
        assertTrue(generated.contains("public fun Server.registerGeneratedMcpTools()"))
        assertTrue(generated.contains("registerGreetUserTool"))
        assertTrue(generated.contains("public fun Server.registerGreetUserTool"))
        assertTrue(generated.contains("name = \"greet_user\""))
        assertTrue(generated.contains("description = \"Greet a user by name.\""))
        assertTrue(generated.contains("put(\"type\", \"string\")"))
        assertTrue(generated.contains("put(\"type\", \"integer\")"))
        assertTrue(generated.contains("putJsonObject(\"display_name\")"))
        assertTrue(generated.contains("required = listOf(\"display_name\")"))
        assertTrue(generated.contains("val namePresent = arguments?.containsKey(\"display_name\") == true"))
        assertTrue(generated.contains("val name = arguments?.get(\"display_name\")?.jsonPrimitive?.contentOrNull"))
        assertTrue(generated.contains("val count = arguments?.get(\"count\")?.jsonPrimitive?.intOrNull"))
        assertTrue(generated.contains("val excited = arguments?.get(\"excited\")?.jsonPrimitive?.booleanOrNull"))
        assertTrue(generated.contains("val result = invokeGreetUserTool"))
        assertTrue(generated.contains("count = count!!"))
        assertTrue(generated.contains("excited = excited"))
        assertTrue(generated.contains("TextContent(result)"))
    }

    @Test
    fun `generates registration code with standard indentation`() {
        val generated = renderGreetTool()

        val expectedRegistration = """
            public fun Server.registerGreetUserTool() {
                addTool(
                    name = "greet_user",
                    description = "Greet a user by name.",
                    inputSchema = ToolSchema(
                        properties = buildJsonObject {
                            putJsonObject("display_name") {
                                put("type", "string")
                                put("description", "Name to greet")
                            }
                            putJsonObject("count") {
                                put("type", "integer")
                                put("description", "How many greetings to generate")
                            }
                            putJsonObject("excited") {
                                put("type", "boolean")
                                put("description", "Whether to add emphasis")
                            }
                        },
                        required = listOf("display_name"),
                    ),
                ) { request ->
                    try {
                        val arguments = request.params.arguments
                        val namePresent = arguments?.containsKey("display_name") == true
                        val name = arguments?.get("display_name")?.jsonPrimitive?.contentOrNull
                        val countPresent = arguments?.containsKey("count") == true
                        val count = arguments?.get("count")?.jsonPrimitive?.intOrNull
                        val excitedPresent = arguments?.containsKey("excited") == true
                        val excited = arguments?.get("excited")?.jsonPrimitive?.booleanOrNull
                        if (namePresent && name == null) {
                            return@addTool invalidArgumentResult("display_name")
                        }
                        if (name == null) {
                            return@addTool missingRequiredArgumentResult("display_name")
                        }
                        if (countPresent && count == null) {
                            return@addTool invalidArgumentResult("count")
                        }
                        val result = invokeGreetUserTool(
                            name = name,
                            count = count,
                            countPresent = countPresent,
                            excited = excited,
                        )
                        return@addTool CallToolResult(
                            content = listOf(TextContent(result)),
                            isError = false,
                        )
                    } catch (exception: Exception) {
                        return@addTool CallToolResult(
                            content = listOf(TextContent(exception.message ?: "Tool failed")),
                            isError = true,
                        )
                    }
                }
            }
        """.trimIndent()

        assertTrue(
            actual = generated.contains(expectedRegistration),
            message = generated,
        )
        assertFalse(
            actual = generated.contains("                properties = buildJsonObject"),
            message = generated,
        )
    }

    @Test
    fun `generates handlers that read arguments from request params safely`() {
        val generated = renderGreetTool()

        assertTrue(generated.contains("val arguments = request.params.arguments"))
        assertFalse(generated.contains("val arguments = request.arguments"))
        assertTrue(generated.contains("val namePresent = arguments?.containsKey(\"display_name\") == true"))
        assertTrue(generated.contains("val countPresent = arguments?.containsKey(\"count\") == true"))
        assertTrue(generated.contains("val excitedPresent = arguments?.containsKey(\"excited\") == true"))
        assertFalse(generated.contains("val name = arguments[\"display_name\"]?.jsonPrimitive?.contentOrNull"))
        assertFalse(generated.contains("val count = arguments[\"count\"]?.jsonPrimitive?.intOrNull"))
        assertFalse(generated.contains("val excited = arguments[\"excited\"]?.jsonPrimitive?.booleanOrNull"))
    }

    @Test
    fun `generates targeted conversion and exception handling shapes`() {
        val generated = renderNumericTool()

        assertTrue(generated.contains("val ratio = arguments?.get(\"ratio\")?.jsonPrimitive?.doubleOrNull"))
        assertTrue(generated.contains("if (ratioPresent && ratio == null)"))
        assertTrue(generated.contains("return@addTool invalidArgumentResult(\"ratio\")"))
        assertTrue(generated.contains("private fun invalidArgumentResult(name: String): CallToolResult"))
        assertTrue(generated.contains("TextContent(\"Invalid argument: \$name\")"))
        assertTrue(generated.contains("private fun missingRequiredArgumentResult(name: String): CallToolResult"))
        assertTrue(generated.contains("TextContent(\"Missing required argument: \$name\")"))
        assertTrue(generated.contains("} catch (exception: Exception) {"))
        assertTrue(generated.contains("return@addTool CallToolResult("))
        assertTrue(generated.contains("content = listOf(TextContent(exception.message ?: \"Tool failed\"))"))
        assertTrue(generated.contains("isError = true"))
    }

    @Test
    fun `generates non suspend invocation directly when no defaults exist`() {
        val generated = renderNumericTool()

        assertTrue(generated.contains("val result = com.example.tools.measure("))
        assertFalse(generated.contains("private suspend fun invokeMeasureRatioTool"))
        assertFalse(generated.contains("private fun invokeMeasureRatioTool"))
    }

    @Test
    fun `direct generated invocation uses standard indentation and smart casted arguments`() {
        val generated = renderNumericTool()

        val directInvocation = generated
            .substringAfter("val result = ")
            .substringBefore("\n            return@addTool")

        val invocationLines = directInvocation.lines()
        val continuationIndent = invocationLines
            .drop(1)
            .filter { it.isNotBlank() }
            .minOf { line -> line.indexOfFirst { character -> !character.isWhitespace() } }
        val normalizedInvocation = invocationLines
            .mapIndexed { index, line ->
                when {
                    index == 0 -> line.trimStart()
                    line.isBlank() -> line
                    else -> line.drop(continuationIndent)
                }
            }
            .joinToString("\n")

        val expectedInvocation = """
            com.example.tools.measure(
                ratio = ratio,
            )
        """.trimIndent()

        assertEquals(expectedInvocation, normalizedInvocation)
        assertFalse(
            actual = generated.contains("ratio = ratio!!"),
            message = generated,
        )
    }

    @Test
    fun `direct generated invocation keeps null assertion for optional non null no default parameters`() {
        val generated = renderOptionalNonNullNoDefaultTool()

        assertTrue(generated.contains("input = input!!"), generated)
    }

    @Test
    fun `generates unique helper names for colliding normalized tool names`() {
        val generated = ToolCodeGenerator.render(
            tools = listOf(
                simpleTextTool(toolName = "foo-bar", functionName = "fooBar"),
                simpleTextTool(toolName = "foo_bar", functionName = "fooBarUnderscore"),
                simpleTextTool(toolName = "foo.bar", functionName = "fooBarDot"),
            ),
        )

        assertTrue(generated.contains("registerFooBar1Tool()"))
        assertTrue(generated.contains("registerFooBar2Tool()"))
        assertTrue(generated.contains("registerFooBar3Tool()"))
        assertTrue(generated.contains("public fun Server.registerFooBar1Tool()"))
        assertTrue(generated.contains("public fun Server.registerFooBar2Tool()"))
        assertTrue(generated.contains("public fun Server.registerFooBar3Tool()"))
        assertTrue(generated.contains("private fun invokeFooBar1Tool("))
        assertTrue(generated.contains("private fun invokeFooBar2Tool("))
        assertTrue(generated.contains("private fun invokeFooBar3Tool("))
    }

    @Test
    fun `generates suspend and non suspend invocation shapes for wrappers`() {
        val generated = ToolCodeGenerator.render(
            tools = listOf(
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
                            schemaName = "id",
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

        assertTrue(generated.contains("registerCleanupTool"))
        assertTrue(generated.contains("registerPassthroughTool"))
        assertTrue(generated.contains("val result = com.example.tools.cleanup("))
        assertTrue(generated.contains("TextContent(\"[OK]\")"))
        assertTrue(generated.contains("val result = com.example.tools.passthrough("))
        assertTrue(generated.contains("return@addTool result"))
        assertTrue(generated.contains("arguments?.get(\"id\")?.jsonPrimitive?.longOrNull"))
    }

    @Test
    fun `helper generated invocation uses return if expression`() {
        val generated = renderGreetTool()
        val helper = generated.substringAfter("private fun invokeGreetUserTool(").substringBefore("private fun missingRequiredArgumentResult")
        assertTrue(helper.contains("= if (countPresent)"))

        val ifBranch = helper.substringAfter("if (countPresent) {\n").substringBefore("\n} else")

        val expectedIfBlock = "    com.example.tools.greet(\n" +
            "        name = name!!,\n" +
            "        count = count!!,\n" +
            "        excited = excited,\n" +
            "    )"

        assertEquals(expectedIfBlock, ifBranch)
    }

    @Test
    fun `omits defaulted parameter from generated function call when argument is absent`() {
        val generated = renderGreetTool()
        val helper = generated.substringAfter("private fun invokeGreetUserTool(").substringBefore("private fun missingRequiredArgumentResult")

        assertTrue(helper.contains("if (countPresent)"))
        assertTrue(helper.contains("count = count!!"))
        assertTrue(helper.contains("else"))
        val elseBranch = helper.substringAfter("else").substringBeforeLast("}")
        assertFalse(elseBranch.contains("count = count!!"))
        assertTrue(elseBranch.contains("name = name!!"))
        assertTrue(elseBranch.contains("excited = excited"))
    }

    @Test
    fun `generates aggregate declaration registration function`() {
        val generated = ToolCodeGenerator.render(
            tools = listOf(simpleTextTool(toolName = "echo", functionName = "echo")),
            resources = listOf(
                ResourceFunction(
                    packageName = "com.example.resources",
                    functionName = "config",
                    resourceName = "config",
                    description = "Configuration.",
                    location = ResourceLocation.Static("file:///config"),
                    mimeType = "application/json",
                    isSuspend = false,
                    parameters = emptyList(),
                    returnType = ResourceReturnType.TextType,
                ),
            ),
            prompts = listOf(
                PromptFunction(
                    packageName = "com.example.prompts",
                    functionName = "summarize",
                    promptName = "summarize",
                    description = "Summarize text.",
                    isSuspend = false,
                    parameters = emptyList(),
                    returnType = PromptReturnType.TextType,
                ),
            ),
        )

        assertTrue(generated.contains("public fun Server.registerGeneratedMcpDeclarations()"), generated)
        assertTrue(generated.contains("registerGeneratedMcpTools()"), generated)
        assertTrue(generated.contains("registerGeneratedMcpResources()"), generated)
        assertTrue(generated.contains("registerGeneratedMcpPrompts()"), generated)
    }

    @Test
    fun `generates static resource registration`() {
        val generated = ToolCodeGenerator.render(
            tools = emptyList(),
            resources = listOf(
                ResourceFunction(
                    packageName = "com.example.resources",
                    functionName = "config",
                    resourceName = "config",
                    description = "Configuration.",
                    location = ResourceLocation.Static("file:///config"),
                    mimeType = "application/json",
                    isSuspend = false,
                    parameters = emptyList(),
                    returnType = ResourceReturnType.TextType,
                ),
            ),
            prompts = emptyList(),
        )

        assertTrue(generated.contains("public fun Server.registerGeneratedMcpResources()"), generated)
        assertTrue(generated.contains("addResource("), generated)
        assertTrue(generated.contains("uri = \"file:///config\""), generated)
        assertTrue(generated.contains("name = \"config\""), generated)
        assertTrue(generated.contains("mimeType = \"application/json\""), generated)
        assertTrue(generated.contains("TextResourceContents("), generated)
        assertTrue(generated.contains("text = result"), generated)
    }

    @Test
    fun `generates native resource content returns`() {
        val generated = ToolCodeGenerator.render(
            tools = emptyList(),
            resources = listOf(
                ResourceFunction(
                    packageName = "com.example.resources",
                    functionName = "blob",
                    resourceName = "blob",
                    description = "Blob.",
                    location = ResourceLocation.Static("file:///blob"),
                    mimeType = "application/octet-stream",
                    isSuspend = false,
                    parameters = emptyList(),
                    returnType = ResourceReturnType.BlobResourceContentsType,
                ),
            ),
            prompts = emptyList(),
        )

        assertTrue(generated.contains("return@addResource ReadResourceResult("), generated)
        assertTrue(generated.contains("contents = listOf(result)"), generated)
    }

    @Test
    fun `generates URI template resource registration`() {
        val generated = ToolCodeGenerator.render(
            tools = emptyList(),
            resources = listOf(
                ResourceFunction(
                    packageName = "com.example.resources",
                    functionName = "trackMetadata",
                    resourceName = "track_metadata",
                    description = "Track metadata.",
                    location = ResourceLocation.Template("audio://tracks/{id}/metadata"),
                    mimeType = "application/json",
                    isSuspend = false,
                    parameters = listOf(
                        ToolParameter(
                            name = "id",
                            schemaName = "id",
                            description = "id",
                            type = ParameterType.StringType,
                            nullable = false,
                            hasDefault = false,
                            required = true,
                        ),
                    ),
                    returnType = ResourceReturnType.TextType,
                ),
            ),
            prompts = emptyList(),
        )

        assertTrue(generated.contains("addResourceTemplate("), generated)
        assertTrue(generated.contains("uriTemplate = \"audio://tracks/{id}/metadata\""), generated)
        assertTrue(generated.contains(") { request, variables ->"), generated)
        assertTrue(generated.contains("val id = variables[\"id\"]"), generated)
        assertTrue(generated.contains("id = id"), generated)
    }

    @Test
    fun `generates prompt registration with arguments`() {
        val generated = ToolCodeGenerator.render(
            tools = emptyList(),
            resources = emptyList(),
            prompts = listOf(
                PromptFunction(
                    packageName = "com.example.prompts",
                    functionName = "summarize",
                    promptName = "summarize_audio",
                    description = "Summarize audio.",
                    isSuspend = false,
                    parameters = listOf(
                        ToolParameter(
                            name = "audioPath",
                            schemaName = "audio_path",
                            description = "Audio path",
                            type = ParameterType.StringType,
                            nullable = false,
                            hasDefault = false,
                            required = true,
                        ),
                    ),
                    returnType = PromptReturnType.TextType,
                ),
            ),
        )

        assertTrue(generated.contains("public fun Server.registerGeneratedMcpPrompts()"), generated)
        assertTrue(generated.contains("addPrompt("), generated)
        assertTrue(generated.contains("name = \"summarize_audio\""), generated)
        assertTrue(generated.contains("PromptArgument("), generated)
        assertTrue(generated.contains("name = \"audio_path\""), generated)
        assertTrue(generated.contains("required = true"), generated)
        assertTrue(generated.contains("val audioPath = arguments?.get(\"audio_path\")"), generated)
        assertTrue(generated.contains("GetPromptResult("), generated)
        assertTrue(generated.contains("Role.User"), generated)
        assertFalse(generated.contains("Role.USER"), generated)
    }

    @Test
    fun `generates prompt primitive parsing and native result return`() {
        val generated = ToolCodeGenerator.render(
            tools = emptyList(),
            resources = emptyList(),
            prompts = listOf(
                PromptFunction(
                    packageName = "com.example.prompts",
                    functionName = "ranked",
                    promptName = "ranked",
                    description = "Ranked prompt.",
                    isSuspend = false,
                    parameters = listOf(
                        ToolParameter("limit", "limit", "Limit", ParameterType.IntType, nullable = false, hasDefault = false, required = true),
                        ToolParameter("verbose", "verbose", "Verbose", ParameterType.BooleanType, nullable = true, hasDefault = false, required = false),
                    ),
                    returnType = PromptReturnType.GetPromptResultType,
                ),
            ),
        )

        assertTrue(generated.contains("val limitPresent = arguments?.containsKey(\"limit\") == true"), generated)
        assertTrue(generated.contains("val limit = arguments?.get(\"limit\")?.toIntOrNull()"), generated)
        assertTrue(generated.contains("if (limitPresent && limit == null)"), generated)
        assertTrue(generated.contains("return@addPrompt promptErrorResult(\"Invalid argument: limit\")"), generated)
        assertTrue(generated.contains("val verbose = arguments?.get(\"verbose\")?.toBooleanStrictOrNull()"), generated)
        assertTrue(generated.contains("return@addPrompt result"), generated)
    }

    @Test
    fun `generates native prompt message returns`() {
        val generated = ToolCodeGenerator.render(
            tools = emptyList(),
            resources = emptyList(),
            prompts = listOf(
                PromptFunction(
                    packageName = "com.example.prompts",
                    functionName = "singleMessage",
                    promptName = "single_message",
                    description = "Single message.",
                    isSuspend = false,
                    parameters = emptyList(),
                    returnType = PromptReturnType.PromptMessageType,
                ),
                PromptFunction(
                    packageName = "com.example.prompts",
                    functionName = "messageList",
                    promptName = "message_list",
                    description = "Message list.",
                    isSuspend = false,
                    parameters = emptyList(),
                    returnType = PromptReturnType.PromptMessageListType,
                ),
            ),
        )

        assertTrue(generated.contains("messages = listOf(result)"), generated)
        assertTrue(generated.contains("messages = result"), generated)
    }

    @Test
    fun `extracts URI template variables in declaration order`() {
        assertEquals(listOf("artist", "track"), extractUriTemplateVariables("audio://{artist}/tracks/{track}"))
    }

    @Test
    fun `ignores duplicate URI template variables after first occurrence`() {
        assertEquals(listOf("id"), extractUriTemplateVariables("audio://tracks/{id}/related/{id}"))
    }

    @Test
    fun `resource location identifies static and template resources`() {
        assertEquals(ResourceLocation.Static("file:///config"), ResourceLocation.from(uri = "file:///config", uriTemplate = ""))
        assertEquals(ResourceLocation.Template("audio://tracks/{id}"), ResourceLocation.from(uri = "", uriTemplate = "audio://tracks/{id}"))
        assertNull(ResourceLocation.from(uri = "", uriTemplate = ""))
        assertNull(ResourceLocation.from(uri = "file:///config", uriTemplate = "audio://tracks/{id}"))
    }

    @Test
    fun `generates tool registration with context injection`() {
        val generated = ToolCodeGenerator.render(
            tools = listOf(
                ToolFunction(
                    packageName = "com.example.tools",
                    functionName = "myTool",
                    toolName = "my_tool",
                    description = "A tool with context.",
                    isSuspend = true,
                    parameters = listOf(
                        ToolParameter(
                            name = "input",
                            schemaName = "input",
                            description = "Input value",
                            type = ParameterType.StringType,
                            nullable = false,
                            hasDefault = false,
                            required = true,
                        ),
                    ),
                    contextParameters = listOf(
                        ContextParameter(name = "conn", type = ContextParameterType.ClientConnection),
                        ContextParameter(name = "req", type = ContextParameterType.CallToolRequest),
                        ContextParameter(name = "srv", type = ContextParameterType.Server),
                    ),
                    returnType = ToolReturnType.TextType,
                ),
            ),
        )

        assertTrue(generated.contains("name = \"my_tool\""), generated)
        assertTrue(generated.contains("conn = this,"), generated)
        assertTrue(generated.contains("req = request,"), generated)
        assertTrue(generated.contains("srv = this@registerMyToolTool,"), generated)
    }

    @Test
    fun `generates static resource registration with context injection`() {
        val generated = ToolCodeGenerator.render(
            tools = emptyList(),
            resources = listOf(
                ResourceFunction(
                    packageName = "com.example.resources",
                    functionName = "config",
                    resourceName = "config",
                    description = "Configuration.",
                    location = ResourceLocation.Static("file:///config"),
                    mimeType = "application/json",
                    isSuspend = false,
                    parameters = emptyList(),
                    contextParameters = listOf(
                        ContextParameter(name = "conn", type = ContextParameterType.ClientConnection),
                        ContextParameter(name = "req", type = ContextParameterType.ReadResourceRequest),
                    ),
                    returnType = ResourceReturnType.TextType,
                ),
            ),
            prompts = emptyList(),
        )

        assertTrue(generated.contains("conn = this,"), generated)
        assertTrue(generated.contains("req = request,"), generated)
    }

    @Test
    fun `generates prompt registration with context injection`() {
        val generated = ToolCodeGenerator.render(
            tools = emptyList(),
            resources = emptyList(),
            prompts = listOf(
                PromptFunction(
                    packageName = "com.example.prompts",
                    functionName = "summarize",
                    promptName = "summarize",
                    description = "Summarize.",
                    isSuspend = false,
                    parameters = emptyList(),
                    contextParameters = listOf(
                        ContextParameter(name = "conn", type = ContextParameterType.ClientConnection),
                        ContextParameter(name = "req", type = ContextParameterType.GetPromptRequest),
                        ContextParameter(name = "srv", type = ContextParameterType.Server),
                    ),
                    returnType = PromptReturnType.TextType,
                ),
            ),
        )

        assertTrue(generated.contains("conn = this,"), generated)
        assertTrue(generated.contains("req = request,"), generated)
        assertTrue(generated.contains("srv = this@registerSummarizePrompt,"), generated)
    }

    private fun renderGreetTool(): String = ToolCodeGenerator.render(
        tools = listOf(
            ToolFunction(
                packageName = "com.example.tools",
                functionName = "greet",
                toolName = "greet_user",
                description = "Greet a user by name.",
                isSuspend = false,
                parameters = listOf(
                    ToolParameter(
                        name = "name",
                        schemaName = "display_name",
                        description = "Name to greet",
                        type = ParameterType.StringType,
                        nullable = false,
                        hasDefault = false,
                        required = true,
                    ),
                    ToolParameter(
                        name = "count",
                        schemaName = "count",
                        description = "How many greetings to generate",
                        type = ParameterType.IntType,
                        nullable = false,
                        hasDefault = true,
                        required = false,
                    ),
                    ToolParameter(
                        name = "excited",
                        schemaName = "excited",
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

    private fun renderNumericTool(): String = ToolCodeGenerator.render(
        tools = listOf(
            ToolFunction(
                packageName = "com.example.tools",
                functionName = "measure",
                toolName = "measure_ratio",
                description = "Measure a ratio.",
                isSuspend = false,
                parameters = listOf(
                    ToolParameter(
                        name = "ratio",
                        schemaName = "ratio",
                        description = "Ratio to parse",
                        type = ParameterType.DoubleType,
                        nullable = false,
                        hasDefault = false,
                        required = true,
                    ),
                ),
                returnType = ToolReturnType.PrimitiveType,
            ),
        ),
    )

    private fun renderOptionalNonNullNoDefaultTool(): String = ToolCodeGenerator.render(
        tools = listOf(
            ToolFunction(
                packageName = "com.example.tools",
                functionName = "optionalValue",
                toolName = "optional_value",
                description = "Accept an optional non-null value.",
                isSuspend = false,
                parameters = listOf(
                    ToolParameter(
                        name = "input",
                        schemaName = "value",
                        description = "Optional value",
                        type = ParameterType.StringType,
                        nullable = false,
                        hasDefault = false,
                        required = false,
                    ),
                ),
                returnType = ToolReturnType.TextType,
            ),
        ),
    )

    private fun simpleTextTool(toolName: String, functionName: String): ToolFunction = ToolFunction(
        packageName = "com.example.tools",
        functionName = functionName,
        toolName = toolName,
        description = "Tool $toolName.",
        isSuspend = false,
        parameters = listOf(
            ToolParameter(
                name = "value",
                schemaName = "value",
                description = "Value to echo",
                type = ParameterType.StringType,
                nullable = false,
                hasDefault = true,
                required = false,
            ),
        ),
        returnType = ToolReturnType.TextType,
    )
}
