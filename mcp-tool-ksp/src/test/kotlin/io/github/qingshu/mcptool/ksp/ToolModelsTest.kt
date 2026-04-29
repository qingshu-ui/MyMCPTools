package io.github.qingshu.mcptool.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolModelsTest {
    @Test
    fun `maps supported Kotlin types to JSON schema types`() {
        assertEquals(ParameterType.StringType, ParameterType.fromQualifiedName("kotlin.String"))
        assertEquals(ParameterType.IntType, ParameterType.fromQualifiedName("kotlin.Int"))
        assertEquals(ParameterType.LongType, ParameterType.fromQualifiedName("kotlin.Long"))
        assertEquals(ParameterType.DoubleType, ParameterType.fromQualifiedName("kotlin.Double"))
        assertEquals(ParameterType.BooleanType, ParameterType.fromQualifiedName("kotlin.Boolean"))
    }

    @Test
    fun `returns null for unsupported Kotlin types`() {
        assertNull(ParameterType.fromQualifiedName("kotlin.collections.List"))
        assertNull(ParameterType.fromQualifiedName("com.example.Custom"))
    }

    @Test
    fun `maps parameter types to JSON schema names`() {
        assertEquals("string", ParameterType.StringType.jsonSchemaType)
        assertEquals("integer", ParameterType.IntType.jsonSchemaType)
        assertEquals("integer", ParameterType.LongType.jsonSchemaType)
        assertEquals("number", ParameterType.DoubleType.jsonSchemaType)
        assertEquals("boolean", ParameterType.BooleanType.jsonSchemaType)
    }
}
