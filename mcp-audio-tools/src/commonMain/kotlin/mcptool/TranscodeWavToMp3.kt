package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcptool.annotations.McpTool
import io.github.qingshu.mcptool.annotations.ToolParam
import io.github.qingshu.process.ProcessBuilder
import io.github.qingshu.process.awaitExit
import io.github.qingshu.process.stderrLines
import io.github.qingshu.process.stdoutLines
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem as fs

@McpTool(
    name = "transcode_wav_to_mp3",
    description = """
        Uses ffmpeg to transcode a single .wav file to .mp3.
        Returns the output path on success.
    """,
)
suspend fun transcodeWavToMp3(
    @ToolParam(description = "Absolute path to the source .wav file.")
    input_path: String,
    @ToolParam(description = "Absolute path for the output .mp3 file.")
    output_path: String,
): String {
    val cmd = makeFfmpegCmd(input_path, output_path)
    createParentDirectories(output_path)
    val process = ProcessBuilder(*cmd)
        .mergeStderr(true)
        .start()

    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val exitCode = coroutineScope {
        launch {
            process.stdoutLines().collect(stdout::appendLine)
        }
        launch {
            process.stderrLines().collect(stderr::appendLine)
        }
        process.awaitExit()
    }

    if (exitCode == 0) {
        return "[OK] $output_path"
    }

    error("[Failed] ffmpeg failed (exit $exitCode): \n$stderr")
}

private fun createParentDirectories(path: String) {
    val output = Path(path)
    output.parent?.let(fs::createDirectories)
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
