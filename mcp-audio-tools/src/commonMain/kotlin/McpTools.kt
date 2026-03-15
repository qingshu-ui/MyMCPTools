package io.github.qingshu.mcpaudiotools

import io.github.qingshu.mcpaudiotools.mcptool.executeCommand
import io.github.qingshu.mcpaudiotools.mcptool.subTitleToLrc
import io.github.qingshu.mcpaudiotools.mcptool.transcodeWavToMp3
import io.modelcontextprotocol.kotlin.sdk.server.Server

fun Server.mcpToolRegistry() {
    transcodeWavToMp3()
    subTitleToLrc()
    executeCommand()
}
