package io.github.qingshu.mcpaudiotools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Server.convertWavToMp3() {
    addTool(
        name = "transcode_wav_to_mp3",
        description = """
            Uses ffmpeg to transcode a single .wav file to .mp3.
            Returns the output path on success.
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("input_path") {
                    put("type", "string")
                    put("description", "Absolute path to the source .wav file.")
                }
                putJsonObject("output_path") {
                    put("type", "string")
                    put(
                        "description",
                        "Absolute path for the output .mp3 file. Parent directory must already exist.",
                    )
                }
            },
            required = listOf("input_path", "output_path"),
        ),
    ) { request ->
        val input = request.params.arguments?.get("input_path")?.jsonPrimitive?.content
        val output = request.params.arguments?.get("output_path")?.jsonPrimitive?.content

        val missing = buildList {
            if (input == null) add("input_path")
            if (output == null) add("output_path")
        }
        if(missing.isNotEmpty()) {
            return@addTool CallToolResult(
                content = listOf(TextContent(
                    "Missing required arguments: ${missing.joinToString()}"
                ))
            )
        }

        // TODO: Not implemented yet.

        CallToolResult(content = emptyList())
    }
}
