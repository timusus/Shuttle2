package com.simplecityapps.shuttle.ui.common.components

import io.kotest.matchers.shouldBe
import org.junit.Test

class SnowfallTest {
    @Test
    fun `a flake moves by its velocity each frame`() {
        val flake = flake(startY = 0f)
        val snowflakes = mutableListOf(flake)

        snowflakes.fall(height = 100, stopping = false)

        flake.snowX shouldBe 11f
        flake.snowY shouldBe 5f
    }

    @Test
    fun `a flake that falls off the bottom goes back to the top while it's snowing`() {
        val flake = flake(startY = -10f)
        val snowflakes = mutableListOf(flake)

        repeat(23) { snowflakes.fall(height = 100, stopping = false) }

        snowflakes shouldBe listOf(flake)
        flake.snowX shouldBe 10f
        flake.snowY shouldBe -10f
    }

    @Test
    fun `a flake that falls off the bottom is dropped once the snow is stopping`() {
        val falling = flake(startY = 96f)
        val above = flake(startY = 0f)
        val snowflakes = mutableListOf(falling, above)

        snowflakes.fall(height = 100, stopping = true)

        snowflakes shouldBe listOf(above)
    }

    private fun flake(startY: Float) = Snowflake(startX = 10f, startY = startY, velX = 1f, velY = 5f, snowR = 4f, alpha = 200)
}
