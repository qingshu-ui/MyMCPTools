package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcpaudiotools.SUBTITLE_TO_LRC
import io.github.qingshu.mcpaudiotools.getEnv
import io.github.qingshu.mcpaudiotools.utils.requireArgs
import io.github.qingshu.mcptool.common.Process
import io.github.qingshu.mcptool.common.exec
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Server.subTitleToLrc() {
    addTool(
        name = "subtitle_to_lrc",
        description = """
            Convert the subtitle file to .lrc format
            Supported input formats:
            - .srt
            - .vtt
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("input_path") {
                    put("type", "string")
                    put("description", "Absolute path to the source <.srt|.vtt> file")
                }
                putJsonObject("output_path") {
                    put("type", "string")
                    put("description", "Absolute path for the output .lrc file. Parent directory must already exist.")
                }
            },
            required = listOf("input_path", "output_path"),
        ),
    ) { request ->

        val (input, output) = request.params.arguments
            .requireArgs("input_path", "output_path")
            .getOrElse { e ->
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(e.message ?: "Missing required arguments"),
                    ),
                    isError = true,
                )
            }

        val cmd = getEnv(SUBTITLE_TO_LRC) ?: "subtitle_to_lrc"
        val result = Process.exec(cmd, input, output)

        CallToolResult(
            content = listOf(
                TextContent(
                    if (result.code == 0) {
                        "OK: $output"
                    } else {
                        "subtitle_to_lrc failed (exit ${result.code}): ${result.stderr}"
                    },
                ),
            ),
            isError = result.code != 0,
        )
    }
}
