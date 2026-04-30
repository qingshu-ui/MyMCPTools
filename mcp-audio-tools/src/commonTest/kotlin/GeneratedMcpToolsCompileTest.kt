package io.github.qingshu.mcpaudiotools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedMcpToolsCompileTest {
    @Test
    fun `generated registry registers migrated audio tools`() {
        val server = McpServer(
            name = "audio-tools-test",
            version = "test",
        ) {
            mcpToolRegistry()
        }

        assertEquals(
            setOf("execute_command", "subtitle_to_lrc", "transcode_wav_to_mp3"),
            server.tools.keys,
        )
        assertTrue(server.tools.getValue("execute_command").tool.inputSchema.required.orEmpty().contains("cmd"))
        assertTrue(server.tools.getValue("subtitle_to_lrc").tool.inputSchema.required.orEmpty().containsAll(listOf("input_path", "output_path")))
        assertTrue(server.tools.getValue("transcode_wav_to_mp3").tool.inputSchema.required.orEmpty().containsAll(listOf("input_path", "output_path")))
    }
}
