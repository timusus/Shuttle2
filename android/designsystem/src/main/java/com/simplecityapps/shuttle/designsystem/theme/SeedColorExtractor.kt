package com.simplecityapps.shuttle.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.toHct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
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
 * The seed colour of [bitmap], or null when it has no colour worth theming with. Of the swatches
 * ranked by chroma and population, the first that isn't near-black (see [MIN_PREFERRED_SEED_TONE]),
 * else the first. Quantises a downscaled copy, so call it off the main thread (see
 * [SeedColorCache.getOrExtract]).
 */
fun extractSeedColor(bitmap: Bitmap): Color? {
    val sample = if (bitmap.width > SAMPLE_SIZE || bitmap.height > SAMPLE_SIZE) {
        Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
    } else {
        bitmap
    }
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
    val swatches = Score.score(QuantizerCelebi.quantize(pixels, MAX_SWATCHES), fallbackColorArgb = null)
        .map(::Color)
        .filter(::isUsableSeed)
    return swatches.firstOrNull { it.toHct().tone >= MIN_PREFERRED_SEED_TONE } ?: swatches.firstOrNull()
}

/**
 * An in-memory LRU of seed colours by artwork key, so a seed is extracted once per artwork per
 * process. Remembers misses too, so artwork without a usable seed isn't re-scanned.
 */
class SeedColorCache(private val maxSize: Int = 100) {
    private val entries = object : LinkedHashMap<String, Color>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>): Boolean = size > maxSize
    }

    /** The cached seed for [key]: [ArtworkSeed.Available], [ArtworkSeed.None] for a remembered miss, or null if not cached. */
    operator fun get(key: String): ArtworkSeed? = synchronized(entries) { entries[key] }?.toArtworkSeed()

    fun put(key: String, seed: Color?) {
        synchronized(entries) { entries[key] = seed ?: Color.Unspecified }
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

    private fun Color?.toArtworkSeed(): ArtworkSeed = if (this == null || this == Color.Unspecified) ArtworkSeed.None else ArtworkSeed.Available(this)
}
