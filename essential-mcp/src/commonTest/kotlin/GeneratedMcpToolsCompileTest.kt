package io.github.qingshu.essentialmcp

import io.github.qingshu.mcptool.annotations.McpPrompt
import io.github.qingshu.mcptool.annotations.McpResource
import io.github.qingshu.mcptool.annotations.PromptParam
import io.github.qingshu.mcptool.generated.registerGeneratedMcpDeclarations
import io.github.qingshu.mcptool.generated.registerGeneratedMcpPrompts
import io.github.qingshu.mcptool.generated.registerGeneratedMcpResources
import io.github.qingshu.mcptool.generated.registerGeneratedMcpTools
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedMcpToolsCompileTest {
    @Test
    fun resourceAndPromptAnnotationsAreVisibleToCommonCode() {
        val resource = McpResource::class.simpleName
        val prompt = McpPrompt::class.simpleName
        val param = PromptParam::class.simpleName

        assertEquals("McpResource", resource)
        assertEquals("McpPrompt", prompt)
        assertEquals("PromptParam", param)
    }

    @Test
    fun generatedDeclarationRegistriesCompile() {
        val server = McpServer(
            name = "audio-tools-test",
            version = "test",
        ) {}

        server.registerGeneratedMcpTools()
        server.registerGeneratedMcpResources()
        server.registerGeneratedMcpPrompts()
        server.registerGeneratedMcpDeclarations()
    }

    @Test
    fun `generated registry registers migrated audio tools`() {
        val server = McpServer(
            name = "audio-tools-test",
            version = "test",
        ) {
            registerGeneratedMcpDeclarations()
        }

        assertEquals(
            setOf("execute_command", "subtitle_to_lrc", "test", "transcode_wav_to_mp3", "understand_image"),
            server.tools.keys,
        )
        assertTrue(server.tools.getValue("execute_command").tool.inputSchema.required.orEmpty().contains("cmd"))
        assertTrue(server.tools.getValue("subtitle_to_lrc").tool.inputSchema.required.orEmpty().containsAll(listOf("input_path", "output_path")))
        assertTrue(server.tools.getValue("transcode_wav_to_mp3").tool.inputSchema.required.orEmpty().containsAll(listOf("input_path", "output_path")))
    }

    @Test
    fun `generated registry registers sample resources and prompts`() {
        val server = McpServer(
            name = "audio-tools-test",
            version = "test",
        ) {
            registerGeneratedMcpDeclarations()
        }

        assertTrue(server.resources.keys.contains("audio://server/info"))
        assertTrue(server.resourceTemplates.any { template -> template.name == "audio_file_summary" })
        assertTrue(server.prompts.keys.contains("summarize_audio"))
    }
}
