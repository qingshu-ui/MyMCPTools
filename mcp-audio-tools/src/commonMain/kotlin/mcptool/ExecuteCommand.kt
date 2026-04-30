package io.github.qingshu.mcpaudiotools.mcptool

import io.github.qingshu.mcptool.annotations.McpTool
import io.github.qingshu.mcptool.annotations.ToolParam
import io.github.qingshu.mcptool.generated.registerExecuteCommandTool
import io.github.qingshu.process.ProcessBuilder
import io.github.qingshu.process.awaitExit
import io.github.qingshu.process.stderrLines
import io.github.qingshu.process.stdoutLines
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@McpTool(
    name = "execute_command",
    description = """
        Execute commands to run any executable program supported by the system,
        such as: 'python --version', 'ls -l'
    """,
)
suspend fun executeCommand(
    @ToolParam(description = "The command to execute, e.g. 'ls -l' or 'python script.py'")
    cmd: String,
    @ToolParam(description = "Optional working directory for the command.")
    cwd: String? = null,
): String {
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

    return if (exitCode == 0) {
        when {
            stdout.isNotEmpty() -> stdout.toString()
            else -> "[Ok] The command no output. Contact developer if unexpected."
        }
    } else {
        val content = "\n- stdout: \n$stdout\n- stderr: \n$stderr"
        error("[Failed] The command execute failed: $content")
    }
}

fun Server.executeCommand() {
    registerExecuteCommandTool()
}
