package io.github.qingshu.essentialmcp

import kotlin.test.Test
import kotlin.test.assertEquals

class CommonTest {
    @Test
    fun collectionLiterals() {
        val list: List<String> = ["a", "b", "c"]
        assertEquals(3, list.size)
        assertEquals("a", list[0])

        val numbers: List<Int> = [1, 2, 3, 4, 5]
        assertEquals(5, numbers.size)

        val mixed: List<Any> = ["hello", 42, true]
        assertEquals(3, mixed.size)
    }
}
