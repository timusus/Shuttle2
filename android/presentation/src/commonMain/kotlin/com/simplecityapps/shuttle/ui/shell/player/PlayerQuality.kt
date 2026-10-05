package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.streaming.DeliveredFormat
import com.simplecityapps.shuttle.ui.screens.songinfo.formatName
import com.simplecityapps.shuttle.ui.screens.songinfo.formatSampleRate

private val losslessCodecs = setOf("FLAC", "ALAC", "WAV", "WAVE", "AIFF", "AIF", "APE", "WV", "WAVPACK", "PCM", "DSD", "DSF", "DFF", "TTA")

/**
 * Now Playing's quality line: the codec, then what the file says about its quality. A lossy codec reads
 * "MP3 · 320 kbps", or the codec and sample rate when the bit rate is unknown. A lossless codec reads
 * "FLAC · 24-bit / 96 kHz" when both are known, else the sample rate, else the bit rate. Null when the
 * song carries no format at all.
 *
 * While the server transcodes the song, it's what's [delivered] instead: "MP3 · 128 kbps", or the codec alone when the
 * server picks the bitrate (#902). Song Info keeps the file's own format.
 */
fun Song.qualityLine(delivered: DeliveredFormat? = null): String? {
    if (delivered != null) return listOfNotNull(delivered.codec, delivered.bitrateKbps?.let { "$it kbps" }).joinToString(" · ")
    val rawCodec = audioCodec?.trim()?.takeIf { it.isNotEmpty() }
    val codec = if (rawCodec.isRawPcm()) "PCM" else rawCodec?.uppercase() ?: formatName(mimeType)
    val rate = bitRate?.takeIf { it > 0 }?.let { "$it kbps" }
    val sample = sampleRate?.takeIf { it > 0 }?.let(::formatSampleRate)
    val depth = bitDepth?.takeIf { it > 0 } ?: rawCodec.pcmBitDepth()
    val lossless = codec?.uppercase() in losslessCodecs
    val detail = if (lossless) {
        if (depth != null && sample != null) "$depth-bit / $sample" else sample ?: rate
    } else {
        rate ?: sample
    }
    return listOfNotNull(codec, detail).filter { it.isNotEmpty() }.joinToString(" · ").takeIf { it.isNotEmpty() }
}

private val pcmCodec = Regex("pcm_[a-z]+?(\\d+)(le|be)?(_planar)?", RegexOption.IGNORE_CASE)

private fun String?.isRawPcm(): Boolean = this != null && startsWith("pcm_", ignoreCase = true)

/** The bit depth a raw PCM codec name carries ("pcm_s24le" is 24, "pcm_f32le" 32, "pcm_u8" 8), for a server that omits it. */
private fun String?.pcmBitDepth(): Int? = this?.let { pcmCodec.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
