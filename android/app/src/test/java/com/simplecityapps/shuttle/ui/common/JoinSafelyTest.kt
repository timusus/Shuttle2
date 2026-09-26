package com.simplecityapps.shuttle.ui.common

import io.kotest.matchers.shouldBe
import org.junit.Test

class JoinSafelyTest {

    @Test
    fun `joins non-empty items with the separator`() {
        joinSafely(" · ", listOf("a", "b", "c")) shouldBe "a · b · c"
    }

    @Test
    fun `skips null and empty items`() {
        joinSafely(" · ", listOf("a", null, "", "b")) shouldBe "a · b"
    }

    @Test
    fun `returns the default value when every item is null or empty`() {
        joinSafely(" · ", listOf(null, ""), defaultValue = "Unknown") shouldBe "Unknown"
    }

    @Test
    fun `returns null when every item is null or empty and no default is given`() {
        joinSafely(" · ", listOf(null, "")) shouldBe null
    }

    @Test
    fun `returns null for an empty list with no default`() {
        joinSafely(" · ", emptyList<String>()) shouldBe null
    }
}
