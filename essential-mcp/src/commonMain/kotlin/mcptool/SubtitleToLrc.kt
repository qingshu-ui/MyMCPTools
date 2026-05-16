package io.github.qingshu.essentialmcp.mcptool

import io.github.qingshu.essentialmcp.SUBTITLE_TO_LRC
import io.github.qingshu.essentialmcp.getEnv
import io.github.qingshu.mcptool.annotations.McpTool
import io.github.qingshu.mcptool.annotations.ToolParam
import io.github.qingshu.process.Process
import io.github.qingshu.process.exec
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem as fs

@Deprecated("Will be removed in the next major version.", level = DeprecationLevel.WARNING)
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
    @ToolParam(description = "Absolute path to the source .srt or .vtt file", name = "input_path")
    inputPath: String,
    @ToolParam(description = "Absolute path for the output .lrc file.", name = "output_path")
    outputPath: String,
): String {
    val cmd = getEnv(SUBTITLE_TO_LRC) ?: "subtitle_to_lrc"
    createParentDirectories(outputPath)
    val result = Process.exec(cmd, inputPath, outputPath)

    if (result.code == 0) {
        return "[OK] $outputPath"
    }

    error("[Failed] subtitle_to_lrc failed (exit ${result.code}): \n${result.stderr}")
}

private fun createParentDirectories(path: String) {
    val output = Path(path)
    output.parent?.let(fs::createDirectories)
}
