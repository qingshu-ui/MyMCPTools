package io.github.qingshu.mcptool.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSValueArgument
import io.github.qingshu.mcptool.annotations.Required
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}

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
