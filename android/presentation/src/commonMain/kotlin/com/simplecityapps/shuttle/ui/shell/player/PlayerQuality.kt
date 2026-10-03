package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.songinfo.formatName
import com.simplecityapps.shuttle.ui.screens.songinfo.formatSampleRate

private val losslessCodecs = setOf("FLAC", "ALAC", "WAV", "WAVE", "AIFF", "AIF", "APE", "WV", "WAVPACK", "PCM", "DSD", "DSF", "DFF", "TTA")

/**
 * Now Playing's quality line: the codec, then what the file says about its quality. A lossy codec reads
 * "MP3 · 320 kbps", or the codec and sample rate when the bit rate is unknown. A lossless codec reads
 * "FLAC · 24-bit / 96 kHz" when both are known, else the sample rate, else the bit rate. Null when the
 * song carries no format at all.
 */
fun Song.qualityLine(): String? {
    val codec = audioCodec?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: formatName(mimeType)
    val rate = bitRate?.takeIf { it > 0 }?.let { "$it kbps" }
    val sample = sampleRate?.takeIf { it > 0 }?.let(::formatSampleRate)
    val depth = bitDepth?.takeIf { it > 0 }
    val lossless = codec?.uppercase() in losslessCodecs
    val detail = if (lossless) {
        if (depth != null && sample != null) "$depth-bit / $sample" else sample ?: rate
    } else {
        rate ?: sample
    }
    return listOfNotNull(codec, detail).filter { it.isNotEmpty() }.joinToString(" · ").takeIf { it.isNotEmpty() }
}
