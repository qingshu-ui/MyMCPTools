package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcptool.annotations.McpPrompt
import io.github.qingshu.mcptool.annotations.PromptParam

@McpPrompt(
    name = "summarize_audio",
    description = "Create a prompt for summarizing an audio file.",
)
fun summarizeAudioPrompt(
    @PromptParam(description = "Path to the audio file.", name = "audio_path")
    audioPath: String,
): String = "Summarize the audio file at: $audioPath"
