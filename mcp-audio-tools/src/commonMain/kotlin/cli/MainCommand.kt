package io.github.qingshu.mcpaudiotools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import io.github.qingshu.mcpaudiotools.disableKotlinLogging
import io.github.qingshu.mcpaudiotools.runMcpServerUsingStdio

class MainCommand : CliktCommand() {
    override fun help(context: Context): String = super.help(context)
    override fun run() {
        disableKotlinLogging()
        runMcpServerUsingStdio()
    }
}
