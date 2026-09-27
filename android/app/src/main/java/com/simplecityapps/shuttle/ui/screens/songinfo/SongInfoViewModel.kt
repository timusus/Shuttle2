package com.simplecityapps.shuttle.ui.screens.songinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.text.StringKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SongInfoUiState(
    val song: Song? = null,
    val loading: Boolean = true,
)

/** One song's details, kept current with the library (a tag edit shows up straight away). */
class SongInfoViewModel @AssistedInject constructor(
    @Assisted songId: Long,
    observeSongs: ObserveSongs,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(songId: Long): SongInfoViewModel
    }

    val uiState: StateFlow<SongInfoUiState> = observeSongs(SongQuery.SongIds(listOf(songId)))
        .map { songs -> SongInfoUiState(song = songs.firstOrNull(), loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongInfoUiState())
}

/** One row of song info: a label and the value, or null when the song doesn't have one. */
data class SongInfoRow(
    val label: StringKey,
    val value: String?,
)

/** A titled group of song info rows, shown as one card. */
data class SongInfoSection(
    val title: StringKey,
    val rows: List<SongInfoRow>,
)

/** Everything song info shows about [this] song, grouped into its cards, in order. */
fun Song.infoSections(): List<SongInfoSection> = listOf(
    SongInfoSection(
        StringKey.SONG_INFO_SECTION_TAGS,
        listOf(
            SongInfoRow(StringKey.SONG_INFO_TRACK_TITLE, name),
            SongInfoRow(StringKey.SONG_INFO_ARTISTS, artists.takeIf { it.isNotEmpty() }?.joinToString(", ")),
            SongInfoRow(StringKey.SONG_INFO_ALBUM, album),
            SongInfoRow(StringKey.SONG_INFO_ALBUM_ARTIST, albumArtist),
            SongInfoRow(StringKey.SONG_INFO_YEAR, date?.year?.toString()),
            SongInfoRow(StringKey.SONG_INFO_TRACK_NUMBER, track?.toString()),
            SongInfoRow(StringKey.SONG_INFO_DISC, disc?.toString()),
            SongInfoRow(StringKey.SONG_INFO_GENRES, genres.takeIf { it.isNotEmpty() }?.joinToString(", ")),
            SongInfoRow(StringKey.SONG_INFO_LYRICS, lyrics),
        ),
    ),
    SongInfoSection(
        StringKey.SONG_INFO_SECTION_FILE,
        listOf(
            SongInfoRow(StringKey.SONG_INFO_PATH, displayPath),
            SongInfoRow(StringKey.SONG_INFO_MIME_TYPE, mimeType),
            SongInfoRow(StringKey.SONG_INFO_SIZE, String.format(Locale.getDefault(), "%.2f MB", size / 1024f / 1024f)),
            SongInfoRow(StringKey.SONG_INFO_DURATION, formatDuration(duration.toLong())),
            SongInfoRow(StringKey.SONG_INFO_BIT_RATE, bitRate?.let(::formatBitRate)),
            SongInfoRow(StringKey.SONG_INFO_BIT_DEPTH, bitDepth?.let { "$it-bit" }),
            SongInfoRow(StringKey.SONG_INFO_SAMPLE_RATE, sampleRate?.let(::formatSampleRate)),
            SongInfoRow(StringKey.SONG_INFO_CHANNEL_COUNT, channelCount?.toString()),
        ),
    ),
    SongInfoSection(
        StringKey.SONG_INFO_SECTION_PLAYBACK,
        listOf(
            SongInfoRow(StringKey.SONG_INFO_PLAY_COUNT, playCount.toString()),
            SongInfoRow(StringKey.SONG_INFO_REPLAY_GAIN_TRACK, replayGainTrack?.let(::formatGain)),
            SongInfoRow(StringKey.SONG_INFO_REPLAY_GAIN_ALBUM, replayGainAlbum?.let(::formatGain)),
        ),
    ),
)

/** The file's headline facts under the artwork, those the song has: its format, bit rate and sample rate. */
fun Song.infoChips(): List<String> = listOfNotNull(
    formatName(mimeType),
    bitRate?.let(::formatBitRate),
    sampleRate?.let(::formatSampleRate),
)

/**
 * The song's path as a person reads it: a Storage Access Framework document URI becomes the path inside its
 * volume ("Music/Album/01 Song.flac"); a file path is shown as it is.
 */
val Song.displayPath: String
    get() {
        if (!path.startsWith("content://") || "/document/" !in path) return path
        return runCatching { URLDecoder.decode(path, Charsets.UTF_8.name()).substringAfterLast(':') }.getOrDefault(path)
    }

/** A sample rate in Hz as kHz: 44100 as "44.1 kHz", 48000 as "48 kHz". */
internal fun formatSampleRate(hz: Int): String = if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.getDefault(), "%.1f kHz", hz / 1000f)

internal fun formatBitRate(kbps: Int): String = "$kbps kb/s"

/** A MIME type as the format people know it: "audio/flac" as "FLAC", "audio/mpeg" as "MP3"; null when it names none. */
internal fun formatName(mimeType: String): String? {
    val subtype = mimeType.substringAfter('/', missingDelimiterValue = "").substringBefore(';').removePrefix("x-").trim().lowercase()
    return when (subtype) {
        "" -> null
        "mpeg", "mp3" -> "MP3"
        "mp4", "m4a", "mp4a-latm" -> "M4A"
        "vorbis" -> "OGG"
        else -> subtype.uppercase()
    }
}

internal fun formatGain(db: Double): String = String.format(Locale.getDefault(), "%+.2f dB", db)
