package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.songinfo.formatName
import com.simplecityapps.shuttle.ui.screens.songinfo.formatSampleRate

/**
 * Now Playing's quality line: the codec, then what the file says about its quality. A song with a bit
 * depth reads "FLAC · 24-bit / 96 kHz"; one with only a bit rate, "MP3 · 320 kbps"; one with only a
 * sample rate, "OGG · 44.1 kHz". Null when the song carries no format at all.
 */
fun Song.qualityLine(): String? {
    val codec = audioCodec?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: formatName(mimeType)
    val detail = when {
        bitDepth != null && bitDepth!! > 0 -> listOfNotNull("$bitDepth-bit", sampleRate?.takeIf { it > 0 }?.let(::formatSampleRate)).joinToString(" / ")
        bitRate != null && bitRate!! > 0 -> "$bitRate kbps"
        sampleRate != null && sampleRate!! > 0 -> formatSampleRate(sampleRate!!)
        else -> null
    }
    return listOfNotNull(codec, detail).joinToString(" · ").takeIf { it.isNotEmpty() }
}
