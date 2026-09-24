package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.ktx.contrastRatio
import com.materialkolor.ktx.toHct
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class ColorSchemesTest {
    private val warm = Color(0xFFD9542B)
    private val grey = Color(0xFF888888)

    @Test
    fun `seeded primary keeps the seed hue`() {
        val scheme = seedColorScheme(warm, isDark = false)
        scheme.primary.toHct().hue shouldBe (warm.toHct().hue plusOrMinus 15.0)
    }

    @Test
    fun `dark schemes have dark surfaces and light schemes light ones`() {
        seedColorScheme(warm, isDark = true).surface.luminance() shouldBeLessThan 0.1f
        seedColorScheme(warm, isDark = false).surface.luminance() shouldBeGreaterThan 0.8f
    }

    @Test
    fun `high contrast raises text contrast against the surface`() {
        val default = seedColorScheme(warm, isDark = false, contrast = S2Contrast.Default)
        val high = seedColorScheme(warm, isDark = false, contrast = S2Contrast.High)
        high.onSurfaceVariant.contrastRatio(high.surface) shouldBeGreaterThan
            default.onSurfaceVariant.contrastRatio(default.surface)
    }

    @Test
    fun `every accent generates a distinct primary`() {
        val primaries = S2Accent.entries.map { accentColorScheme(it, isDark = false).primary }
        primaries.toSet().size shouldBe S2Accent.entries.size
    }

    @Test
    fun `the default accent is not the stock M3 purple`() {
        accentColorScheme(S2Accent.Default, isDark = false).primary shouldNotBe Color(0xFF6750A4)
    }

    @Test
    fun `a grey seed falls back`() {
        isUsableSeed(grey) shouldBe false
        artworkColorScheme(grey, isDark = false).shouldBeNull()
    }

    @Test
    fun `a chromatic seed generates an artwork scheme`() {
        isUsableSeed(warm) shouldBe true
        artworkColorScheme(warm, isDark = false).shouldNotBeNull()
    }

    @Test
    fun `the player style carries more of the seed into containers than the detail style`() {
        val detail = artworkColorScheme(warm, isDark = false, style = ArtworkSchemeStyle.Detail)!!
        val player = artworkColorScheme(warm, isDark = false, style = ArtworkSchemeStyle.Player)!!
        player.primaryContainer.toHct().chroma shouldBeGreaterThan detail.primaryContainer.toHct().chroma
    }

    @Test
    fun `system contrast maps onto levels`() {
        S2Contrast.fromSystemContrast(-1f) shouldBe S2Contrast.Default
        S2Contrast.fromSystemContrast(0f) shouldBe S2Contrast.Default
        S2Contrast.fromSystemContrast(0.5f) shouldBe S2Contrast.Medium
        S2Contrast.fromSystemContrast(1f) shouldBe S2Contrast.High
    }
}
