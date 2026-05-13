package io.github.qingshu.mcptool.ksp

import kotlin.test.Test
import kotlin.test.assertEquals

class ResourcePromptValidatorTest {
    @Test
    fun `missing resource location is invalid`() {
        assertEquals("@McpResource must specify exactly one of uri or uriTemplate.", validateResourceLocation(uri = "", uriTemplate = ""))
    }

    @Test
    fun `resource cannot specify both uri and uri template`() {
        assertEquals(
            "@McpResource must specify exactly one of uri or uriTemplate.",
            validateResourceLocation(uri = "file:///config", uriTemplate = "audio://tracks/{id}"),
        )
    }

    @Test
    fun `template variables must match parameter names`() {
        assertEquals(
            "URI template variable(s) missing matching function parameters: trackId",
            validateUriTemplateParameters(uriTemplate = "audio://tracks/{trackId}", parameterNames = setOf("id")),
        )
    }

    @Test
    fun `static resources cannot declare parameters`() {
        assertEquals(
            "Static @McpResource functions must not declare parameters.",
            validateStaticResourceParameters(parameterNames = listOf("id")),
        )
    }
}
