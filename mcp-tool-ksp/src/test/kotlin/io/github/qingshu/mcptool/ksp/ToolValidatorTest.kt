package io.github.qingshu.mcptool.ksp

import io.github.qingshu.mcptool.annotations.Required
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
