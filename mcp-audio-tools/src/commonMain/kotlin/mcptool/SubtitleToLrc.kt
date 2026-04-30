package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcpaudiotools.SUBTITLE_TO_LRC
import io.github.qingshu.mcpaudiotools.getEnv
import io.github.qingshu.mcptool.annotations.McpTool
import io.github.qingshu.mcptool.annotations.ToolParam
import io.github.qingshu.mcptool.generated.registerSubtitleToLrcTool
import io.github.qingshu.process.Process
import io.github.qingshu.process.exec
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem as fs

@McpTool(
    name = "subtitle_to_lrc",
    description = """
        Convert the subtitle file to .lrc format
        Supported input formats:
        - .srt
        - .vtt
    """,
)
suspend fun subTitleToLrc(
    @ToolParam(description = "Absolute path to the source .srt or .vtt file")
    input_path: String,
    @ToolParam(description = "Absolute path for the output .lrc file.")
    output_path: String,
): String {
    val cmd = getEnv(SUBTITLE_TO_LRC) ?: "subtitle_to_lrc"
    fs.createDirectories(Path(output_path))
    val result = Process.exec(cmd, input_path, output_path)

    if (result.code == 0) {
        return "[OK] $output_path"
    }

    error("[Failed] subtitle_to_lrc failed (exit ${result.code}): \n${result.stderr}")
}

fun Server.subTitleToLrc() {
    registerSubtitleToLrcTool()
}
