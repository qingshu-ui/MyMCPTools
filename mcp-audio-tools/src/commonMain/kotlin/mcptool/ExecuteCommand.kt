package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcpaudiotools.utils.requireArgs
import io.github.qingshu.mcptool.common.ProcessBuilder
import io.github.qingshu.mcptool.common.awaitExit
import io.github.qingshu.mcptool.common.stderrLines
import io.github.qingshu.mcptool.common.stdoutLines
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Server.executeCommand() {
    addTool(
        name = "execute_command",
        description = """
            Execute commands to run any executable program supported by the system, 
            such as: 'dir /c', 'bash ls -l'
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("cmd") {
                    put("type", "string")
                    put("description", "The command to execute, e.g. 'dir /c' or 'python script.py'")
                }
                putJsonObject("cwd") {
                    put("type", "string")
                    put("description", "Optional working directory for the command.")
                }
            },
            required = listOf("cmd"),
        ),
    ) { call ->

        val (cmd) = call.arguments
            .requireArgs("cmd")
            .getOrElse { e ->
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(e.message ?: "Missing required arguments"),
                    ),
                    isError = true,
                )
            }
        val cwd = call.arguments?.get("cwd")?.jsonPrimitive?.contentOrNull

        val process = ProcessBuilder("bash", "-c", cmd).run {
            cwd?.let(::directory)
            start()
        }

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val exitCode = process.awaitExit()
        process.stdoutLines().collect(stdout::appendLine)
        process.stderrLines().collect(stderr::appendLine)

        CallToolResult(
            content = listOf(
                TextContent(
                    if (exitCode == 0) {
                        stdout.toString()
                    } else {
                        """
                            The command '$cmd' execute failed.
                            - stdout: 
                                $stdout
                            - stderr: 
                                $stderr
                        """.trimIndent()
                    },
                ),
            ),
            isError = exitCode != 0,
        )
    }
}
