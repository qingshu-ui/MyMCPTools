package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.squareup.kotlinpoet.ClassName
import io.github.qingshu.mcptool.annotations.Required
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToolValidatorTest {
    @Test
    fun `non-null parameter without default is required by inference`() {
        val required = inferRequiredness(nullable = false, hasDefault = false, explicit = Required.UNSPECIFIED)
        assertEquals(true, required)
    }

    @Test
    fun `nullable parameter is optional by inference`() {
        val required = inferRequiredness(nullable = true, hasDefault = false, explicit = Required.UNSPECIFIED)
        assertEquals(false, required)
    }

    @Test
    fun `default parameter is optional by inference`() {
        val required = inferRequiredness(nullable = false, hasDefault = true, explicit = Required.UNSPECIFIED)
        assertEquals(false, required)
    }

    @Test
    fun `explicit false can mark non-null parameter optional`() {
        val required = inferRequiredness(nullable = false, hasDefault = true, explicit = Required.FALSE)
        assertEquals(false, required)
    }

    @Test
    fun `tool name normalization keeps alphanumeric segments`() {
        assertEquals("FooBar123", "foo-bar_123".normalizedToolFunctionNameComponent())
    }

    @Test
    fun `tool name normalization returns blank when name has no usable characters`() {
        assertEquals("", "--- ... ___".normalizedToolFunctionNameComponent())
    }

    @Test
    fun `no duplicate schema names returns empty set`() {
        val duplicates = duplicateSchemaNames(
            listOf(
                toolParameter(name = "input", schemaName = "input"),
                toolParameter(name = "format", schemaName = "format"),
            ),
        )
        assertEquals(emptySet(), duplicates)
    }

    @Test
    fun `duplicate explicit schema names return that schema name`() {
        val duplicates = duplicateSchemaNames(
            listOf(
                toolParameter(name = "input", schemaName = "source"),
                toolParameter(name = "output", schemaName = "source"),
            ),
        )
        assertEquals(setOf("source"), duplicates)
    }

    @Test
    fun `default schema name colliding with explicit schema name returns colliding name`() {
        val duplicates = duplicateSchemaNames(
            listOf(
                toolParameter(name = "source", schemaName = "source"),
                toolParameter(name = "input", schemaName = "source"),
            ),
        )
        assertEquals(setOf("source"), duplicates)
    }

    @Test
    fun `resolve schema name uses explicit annotation name`() {
        assertEquals("source", resolveSchemaName(annotationName = "source", parameterName = "input"))
    }

    @Test
    fun `resolve schema name falls back to parameter name when blank`() {
        assertEquals("input", resolveSchemaName(annotationName = "   ", parameterName = "input"))
        assertEquals("input", resolveSchemaName(annotationName = null, parameterName = "input"))
    }

    @Test
    fun `duplicate schema validation logs error and rejects parameters`() {
        val logger = recordingLogger()
        val symbol = functionDeclarationSymbol()

        val isValid = validateUniqueSchemaNames(
            parameters = listOf(
                toolParameter(name = "source", schemaName = "input"),
                toolParameter(name = "input", schemaName = "input"),
            ),
            toolName = "transcode",
            logger = logger.delegate,
            symbol = symbol,
        )

        assertEquals(false, isValid)
        assertEquals(
            listOf("Duplicate @ToolParam schema name(s) for tool 'transcode': input"),
            logger.errors,
        )
    }

    @Test
    fun `required enum entry argument resolves from KSP class declaration value`() {
        assertEquals(Required.TRUE, requiredValueArgument(Required.TRUE).toRequiredEnumValue())
        assertEquals(Required.FALSE, requiredValueArgument(Required.FALSE).toRequiredEnumValue())
        assertEquals(Required.UNSPECIFIED, requiredValueArgument(Required.UNSPECIFIED).toRequiredEnumValue())
    }

    @Test
    fun `explicit true on nullable parameter fails`() {
        val error = assertFailsWith<IllegalArgumentException> {
            inferRequiredness(nullable = true, hasDefault = false, explicit = Required.TRUE)
        }
        assertEquals("Parameter cannot be required when its Kotlin type is nullable or has a default value.", error.message)
    }

    @Test
    fun `explicit true on default parameter fails`() {
        val error = assertFailsWith<IllegalArgumentException> {
            inferRequiredness(nullable = false, hasDefault = true, explicit = Required.TRUE)
        }
        assertEquals("Parameter cannot be required when its Kotlin type is nullable or has a default value.", error.message)
    }

    @Test
    fun `resolveReturnType accepts @Serializable custom return type`() {
        val logger = recordingLogger()
        val function = functionDeclarationWithReturnType(
            returnType = ksType(
                qualifiedName = "com.example.tools.ToolResult",
                annotations = listOf(serializableAnnotation()),
            ),
        )

        val result = function.resolveReturnType(logger.delegate)

        assertTrue(result is ToolReturnType.SerializableStructuredType)
        assertEquals(ClassName("com.example.tools", "ToolResult"), (result as ToolReturnType.SerializableStructuredType).typeName)
        assertTrue(logger.errors.isEmpty(), "Expected no errors but got: ${logger.errors}")
    }

    @Test
    fun `resolveReturnType rejects non-@Serializable custom return type`() {
        val logger = recordingLogger()
        val function = functionDeclarationWithReturnType(
            returnType = ksType(
                qualifiedName = "com.example.tools.ToolResult",
                annotations = emptyList(),
            ),
        )

        val result = function.resolveReturnType(logger.delegate)

        assertEquals(null, result)
        assertTrue(logger.errors.any { it.contains("Custom return types must be annotated with @Serializable") })
    }
}

private fun toolParameter(name: String, schemaName: String): ToolParameter = ToolParameter(
    name = name,
    schemaName = schemaName,
    description = "desc",
    type = ParameterType.StringType,
    nullable = false,
    hasDefault = false,
    required = true,
)

private fun requiredValueArgument(required: Required): KSValueArgument = proxyOf(KSValueArgument::class.java) { methodName ->
    when (methodName) {
        "getValue" -> enumClassDeclaration(required)
        "getName" -> null
        "isSpread" -> false
        "getAnnotations" -> emptySequence<Any>()
        "getLocation" -> com.google.devtools.ksp.symbol.NonExistLocation
        "getOrigin" -> com.google.devtools.ksp.symbol.Origin.KOTLIN
        "getParent" -> null
        else -> unsupported(methodName)
    }
}

private fun enumClassDeclaration(required: Required): KSClassDeclaration = proxyOf(KSClassDeclaration::class.java) { methodName ->
    when (methodName) {
        "getSimpleName" -> simpleName(required.name)
        else -> unsupported(methodName)
    }
}

private class RecordingLogger(
    val delegate: KSPLogger,
    val errors: List<String>,
)

private fun recordingLogger(): RecordingLogger {
    val errors = mutableListOf<String>()
    val delegate = Proxy.newProxyInstance(KSPLogger::class.java.classLoader, arrayOf(KSPLogger::class.java)) { _, method, args ->
        when (method.name) {
            "error" -> {
                errors += args?.firstOrNull() as? String ?: ""
                null
            }

            "exception", "info", "logging", "warn" -> null

            "isWarnEnabled", "isInfoEnabled" -> false

            "toString" -> "KSPLoggerProxy"

            "hashCode" -> System.identityHashCode(errors)

            "equals" -> false

            else -> unsupported(method.name)
        }
    } as KSPLogger
    return RecordingLogger(delegate = delegate, errors = errors)
}

private fun functionDeclarationSymbol(): KSFunctionDeclaration = proxyOf(KSFunctionDeclaration::class.java) { methodName ->
    unsupported(methodName)
}

private fun functionDeclarationWithReturnType(returnType: KSType?): KSFunctionDeclaration = proxyOf(KSFunctionDeclaration::class.java) { methodName ->
    when (methodName) {
        "getReturnType" -> if (returnType != null) ksTypeReference(returnType) else null
        else -> unsupported(methodName)
    }
}

private fun ksTypeReference(resolved: KSType): KSTypeReference = proxyOf(KSTypeReference::class.java) { methodName ->
    when (methodName) {
        "resolve" -> resolved
        "getAnnotations" -> emptySequence<KSAnnotation>()
        "getElement" -> null
        "getLocation" -> com.google.devtools.ksp.symbol.NonExistLocation
        "getOrigin" -> com.google.devtools.ksp.symbol.Origin.KOTLIN
        "getParent" -> null
        else -> unsupported(methodName)
    }
}

private fun ksType(qualifiedName: String, annotations: List<KSAnnotation> = emptyList()): KSType = proxyOf(KSType::class.java) { methodName ->
    when (methodName) {
        "getDeclaration" -> ksDeclaration(qualifiedName, annotations)
        "isMarkedNullable" -> false
        "getAnnotations" -> annotations.asSequence()
        "getError" -> null
        "getIsError" -> false
        "getNullability" -> com.google.devtools.ksp.symbol.Nullability.NOT_NULL
        else -> unsupported(methodName)
    }
}

private fun ksDeclaration(qualifiedName: String, annotations: List<KSAnnotation>): KSDeclaration = proxyOf(KSDeclaration::class.java) { methodName ->
    when (methodName) {
        "getQualifiedName" -> simpleName(qualifiedName)
        "getSimpleName" -> simpleName(qualifiedName.substringAfterLast('.'))
        "getAnnotations" -> annotations.asSequence()
        "getLocation" -> com.google.devtools.ksp.symbol.NonExistLocation
        "getOrigin" -> com.google.devtools.ksp.symbol.Origin.KOTLIN
        "getParent" -> null
        "getPackageName" -> simpleName(qualifiedName.substringBeforeLast('.', ""))
        else -> unsupported(methodName)
    }
}

private fun serializableAnnotation(): KSAnnotation = proxyOf(KSAnnotation::class.java) { methodName ->
    when (methodName) {
        "getAnnotationType" -> ksTypeReference(
            ksType(qualifiedName = "kotlinx.serialization.Serializable"),
        )

        "getArguments" -> emptyList<KSValueArgument>()

        "getShortName" -> "Serializable"

        "getLocation" -> com.google.devtools.ksp.symbol.NonExistLocation

        "getOrigin" -> com.google.devtools.ksp.symbol.Origin.KOTLIN

        "getParent" -> null

        "getDefaultValue" -> null

        else -> unsupported(methodName)
    }
}

private fun simpleName(name: String): KSName = proxyOf(KSName::class.java) { methodName ->
    when (methodName) {
        "asString", "getShortName" -> name
        "getQualifier" -> ""
        else -> unsupported(methodName)
    }
}

private fun unsupported(methodName: String): Nothing = throw UnsupportedOperationException("Test stub does not implement $methodName")

@Suppress("UNCHECKED_CAST")
private fun <T> proxyOf(type: Class<T>, handler: (String) -> Any?): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
    when (method.name) {
        "toString" -> "${type.simpleName}Proxy"
        "hashCode" -> System.identityHashCode(handler)
        "equals" -> false
        else -> handler(method.name)
    }
} as T
