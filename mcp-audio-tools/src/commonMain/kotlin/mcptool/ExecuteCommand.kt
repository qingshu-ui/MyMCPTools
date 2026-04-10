package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcpaudiotools.utils.requireArgs
import io.github.qingshu.process.ProcessBuilder
import io.github.qingshu.process.awaitExit
import io.github.qingshu.process.stderrLines
import io.github.qingshu.process.stdoutLines
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
            such as: 'python --version', 'ls -l'
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("cmd") {
                    put("type", "string")
                    put("description", "The command to execute, e.g. 'ls -l' or 'python script.py'")
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
        val exitCode = coroutineScope {
            launch { process.stdoutLines().collect(stdout::appendLine) }
            launch { process.stderrLines().collect(stderr::appendLine) }
            process.awaitExit()
        }

        val content = "\n- stdout: \n$stdout\n- stderr: \n$stderr"
        CallToolResult(
            content = listOf(
                TextContent(
                    if (exitCode == 0) when {
                        stdout.isNotEmpty() -> stdout.toString()
                        else -> "[Ok] The command no output. Contact developer if unexpected."
                    } else {
                        "[Failed] The command execute failed: $content"
                    },
                ),
            ),
            isError = exitCode != 0,
        )
    }
}
