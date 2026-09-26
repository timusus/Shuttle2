package com.simplecityapps.shuttle.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.ktx.toHct
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeedColorCacheTest {
    private val red = Color(0xFFD9542B)

    private fun solidBitmap(color: Color, size: Int = 64): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
    }

    @Test
    fun `extracts the dominant hue from a large bitmap`() {
        val seed = extractSeedColor(solidBitmap(red, size = 500))
        seed!!.toHct().hue shouldBe (red.toHct().hue plusOrMinus 5.0)
    }

    @Test
    fun `a dark cover's seed is its colour, not its near-black background`() {
        // Pink dots over a near-black navy that covers most of it, each shaded like a real cover's
        val cover = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until 100) {
                for (y in 0 until 100) {
                    val shade = (x + y) % 24
                    setPixel(x, y, if (x % 10 < 3 && y % 10 < 3) android.graphics.Color.rgb(200 + shade, 50 + shade, 140) else android.graphics.Color.rgb(12, 10, 28 + shade))
                }
            }
        }
        val seed = extractSeedColor(cover)!!.toHct()
        seed.hue shouldBe (Color(0xFFD74A9A).toHct().hue plusOrMinus 10.0)
    }

    @Test
    fun `an all-dark cover still has a seed`() {
        val navy = Color(0xFF1A1740)
        extractSeedColor(solidBitmap(navy))!!.toHct().hue shouldBe (navy.toHct().hue plusOrMinus 5.0)
    }

    @Test
    fun `a grey bitmap has no seed`() {
        extractSeedColor(solidBitmap(Color(0xFF808080))) shouldBe null
    }

    @Test
    fun `extracts once per key`() = runBlocking<Unit> {
        val cache = SeedColorCache()
        var loads = 0
        val loader: suspend () -> Bitmap? = {
            loads++
            solidBitmap(red)
        }
        cache.getOrExtract("album-1", loader).shouldBeInstanceOf<ArtworkSeed.Available>()
        cache.getOrExtract("album-1", loader).shouldBeInstanceOf<ArtworkSeed.Available>()
        loads shouldBe 1
    }

    @Test
    fun `remembers a miss`() = runBlocking<Unit> {
        val cache = SeedColorCache()
        var loads = 0
        val loader: suspend () -> Bitmap? = {
            loads++
            solidBitmap(Color(0xFF808080))
        }
        cache.getOrExtract("grey", loader) shouldBe ArtworkSeed.None
        cache.getOrExtract("grey", loader) shouldBe ArtworkSeed.None
        loads shouldBe 1
    }

    @Test
    fun `a failed load is not cached`() = runBlocking<Unit> {
        val cache = SeedColorCache()
        cache.getOrExtract("broken") { null } shouldBe ArtworkSeed.None
        cache["broken"] shouldBe null
    }

    @Test
    fun `evicts the least recently used entry`() {
        val cache = SeedColorCache(maxSize = 2)
        cache.put("a", red)
        cache.put("b", red)
        cache["a"] // touch a, so b is now the eldest
        cache.put("c", red)
        cache.size shouldBe 2
        cache["b"] shouldBe null
        cache["a"] shouldBe ArtworkSeed.Available(red)
        cache["c"] shouldBe ArtworkSeed.Available(red)
    }
}
