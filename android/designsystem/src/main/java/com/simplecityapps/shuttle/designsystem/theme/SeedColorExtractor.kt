package com.simplecityapps.shuttle.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.ktx.toHct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Side of the square the artwork is scaled to before quantising: plenty for one seed, cheap to scan. */
private const val SAMPLE_SIZE = 112

/** Colours the artwork is quantised to before they're ranked. */
private const val MAX_SWATCHES = 128

/**
 * Swatches darker than this HCT tone read as near-black: a dark cover's background rather than its
 * colour, so a lighter swatch is preferred as the seed.
 */
const val MIN_PREFERRED_SEED_TONE = 20.0

/**
 * The share of the artwork a lighter swatch's hue must cover to take over from a near-black one: a stray speck on a dark
 * cover (a highlight, a few letters of type) isn't its colour (#735). Counted over the hue's whole family, since thin
 * line art or a halftone splits one colour into many small anti-aliased swatches.
 */
const val MIN_PREFERRED_SEED_SHARE = 0.03

/** Swatches within this many degrees of hue count towards the same colour's share. */
private const val HUE_FAMILY_RANGE = 20.0

/** Swatches below this chroma are greys, with no hue to share. */
private const val HUE_FAMILY_MIN_CHROMA = 8.0

/**
 * The seed colour of [bitmap], or null when it has no colour worth theming with. Of the swatches
 * ranked by chroma and population, the first that isn't near-black (see [MIN_PREFERRED_SEED_TONE])
 * and whose hue covers at least [MIN_PREFERRED_SEED_SHARE] of the image, else the first. Quantises a downscaled
 * copy, so call it off the main thread (see [SeedColorCache.getOrExtract]).
 */
fun extractSeedColor(bitmap: Bitmap): Color? {
    val sample = if (bitmap.width > SAMPLE_SIZE || bitmap.height > SAMPLE_SIZE) {
        Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
    } else {
        bitmap
    }
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
    val populations = QuantizerCelebi.quantize(pixels, MAX_SWATCHES)
    val minPreferredPopulation = pixels.size * MIN_PREFERRED_SEED_SHARE
    val chromatic = populations.mapNotNull { (argb, population) ->
        Color(argb).toHct().takeIf { it.chroma >= HUE_FAMILY_MIN_CHROMA }?.let { it.hue to population }
    }

    fun hueShare(hue: Double): Int = chromatic.sumOf { (other, population) ->
        val distance = abs(other - hue) % 360.0
        if (minOf(distance, 360.0 - distance) <= HUE_FAMILY_RANGE) population else 0
    }
    val swatches = Score.score(populations, fallbackColorArgb = null).filter { isUsableSeed(Color(it)) }
    val preferred = swatches.firstOrNull { argb ->
        val hct = Color(argb).toHct()
        hct.tone >= MIN_PREFERRED_SEED_TONE && hueShare(hct.hue) >= minPreferredPopulation
    }
    return (preferred ?: swatches.firstOrNull())?.let(::Color)
}

/**
 * An in-memory LRU of seed colours by artwork key, so a seed is extracted once per artwork per
 * process. Remembers misses too, so artwork without a usable seed isn't re-scanned.
 */
class SeedColorCache(private val maxSize: Int = 100) {
    private val entries = object : LinkedHashMap<String, ArtworkSeed>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkSeed>): Boolean = size > maxSize
    }

    /** The cached seed for [key]: [ArtworkSeed.Available], [ArtworkSeed.None] for a remembered miss, or null if not cached. */
    operator fun get(key: String): ArtworkSeed? = synchronized(entries) { entries[key] }

    fun put(key: String, seed: Color?) {
        synchronized(entries) { entries[key] = seed.toArtworkSeed() }
    }

    val size: Int get() = synchronized(entries) { entries.size }

    /**
     * The seed for [key], loading the artwork with [loadBitmap] and extracting on
     * [Dispatchers.Default] on a miss. A null bitmap (artwork failed to load) isn't cached.
     */
    suspend fun getOrExtract(key: String, loadBitmap: suspend () -> Bitmap?): ArtworkSeed {
        get(key)?.let { return it }
        val bitmap = loadBitmap() ?: return ArtworkSeed.None
        val seed = withContext(Dispatchers.Default) { extractSeedColor(bitmap) }
        put(key, seed)
        return seed.toArtworkSeed()
    }

    private fun Color?.toArtworkSeed(): ArtworkSeed = if (this == null) ArtworkSeed.None else ArtworkSeed.Available(toArgb())
}
