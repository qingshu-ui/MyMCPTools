package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcpaudiotools.utils.log
import io.github.qingshu.mcpaudiotools.utils.requireArgs
import io.github.qingshu.mcptool.common.ProcessBuilder
import io.github.qingshu.mcptool.common.awaitExit
import io.github.qingshu.mcptool.common.stderrLines
import io.github.qingshu.mcptool.common.stdoutLines
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.io.files.SystemFileSystem as fs

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
                    put("description", "Absolute path for the output .mp3 file.")
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

        val cmd = makeFfmpegCmd(input, output)
        fs.createDirectories(Path(output))
        val process = ProcessBuilder(*cmd)
            .mergeStderr(true)
            .start()

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val exitCode = coroutineScope {
            launch {
                process.stdoutLines().collect { line ->
                    stdout.appendLine(line)
                    log(line)
                }
            }
            launch {
                process.stderrLines().collect(stderr::appendLine)
            }
            process.awaitExit()
        }

        CallToolResult(
            content = listOf(
                TextContent(
                    if (exitCode == 0) {
                        "[OK] $output"
                    } else {
                        "[Failed] ffmpeg failed (exit $exitCode): \n$stderr"
                    },
                ),
            ),
            isError = exitCode != 0,
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
