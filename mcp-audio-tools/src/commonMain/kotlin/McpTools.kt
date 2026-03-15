package io.github.qingshu.mcpaudiotools

import io.github.qingshu.mcptool.common.ProcessBuilder
import io.github.qingshu.mcptool.common.ProcessResult
import io.github.qingshu.mcptool.common.awaitExit
import io.github.qingshu.mcptool.common.stderrLines
import io.github.qingshu.mcptool.common.stdoutLines
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Server.transcodeWavToMp3() {
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
            if (input.isNullOrEmpty()) add("input_path")
            if (output.isNullOrEmpty()) add("output_path")
        }
        if (missing.isNotEmpty()) {
            return@addTool CallToolResult(
                content = listOf(
                    TextContent(
                        "Missing required arguments: ${missing.joinToString()}",
                    ),
                ),
                isError = true,
            )
        }

        val cmd = makeFfmpegCmd(input!!, output!!)
        val process = ProcessBuilder(*cmd)
            .mergeStderr(true)
            .start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        coroutineScope {
            launch {
                process.stdoutLines().collect { line ->
                    stdout.appendLine(line)
                    sendLoggingMessage(
                        notification = LoggingMessageNotification(
                            params = LoggingMessageNotificationParams(
                                level = LoggingLevel.Info,
                                data = JsonPrimitive(line),
                            ),
                        ),
                    )
                }
            }
            launch {
                process.stderrLines().collect(stderr::appendLine)
            }
        }

        val result = ProcessResult(
            code = process.awaitExit(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
        )

        CallToolResult(
            content = listOf(
                TextContent(
                    if (result.code == 0) {
                        "OK: $output"
                    } else {
                        "ffmpeg failed (exit ${result.code}): ${result.stderr}"
                    },
                ),
            ),
            isError = result.code != 0,
        )
    }
}

private fun makeFfmpegCmd(input: String, output: String): Array<String> = arrayOf(
    "ffmpeg",
    "-hide_banner",
    // "-nostats",
    "-progress", "pipe:1",
    "-stats_period", "5",
    "-y",
    "-i", input,
    "-codec:a", "libmp3lame",
    "-qscale:a", "2",
    output,
)
