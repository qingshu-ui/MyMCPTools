package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

public class McpToolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = McpToolProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
    )
}

internal data class ProcessorContext(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
)
