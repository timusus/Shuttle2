package com.simplecityapps.shuttle.ui.screens.songinfo

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
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
@HiltViewModel(assistedFactory = SongInfoViewModel.Factory::class)
class SongInfoViewModel @AssistedInject constructor(
    @Assisted songId: Long,
    songRepository: SongRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(songId: Long): SongInfoViewModel
    }

    val uiState: StateFlow<SongInfoUiState> = songRepository.getSongs(SongQuery.SongIds(listOf(songId)))
        .map { songs -> SongInfoUiState(song = songs?.firstOrNull(), loading = songs == null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongInfoUiState())
}

/** One row of song info: a label and the value, or null when the song doesn't have one. */
data class SongInfoRow(
    @StringRes val label: Int,
    val value: String?,
)

/** Everything song info shows about [this] song, in order. */
fun Song.infoRows(): List<SongInfoRow> = listOf(
    SongInfoRow(R.string.song_info_track_title, name),
    SongInfoRow(R.string.song_info_track_number, track?.toString()),
    SongInfoRow(R.string.song_info_duration, formatDuration(duration.toLong())),
    SongInfoRow(R.string.song_info_album_artist, albumArtist),
    SongInfoRow(R.string.song_info_artists, artists.takeIf { it.isNotEmpty() }?.joinToString(", ")),
    SongInfoRow(R.string.song_info_album, album),
    SongInfoRow(R.string.song_info_year, date?.year?.toString()),
    SongInfoRow(R.string.song_info_disc, disc?.toString()),
    SongInfoRow(R.string.song_info_play_count, playCount.toString()),
    SongInfoRow(R.string.song_info_genres, genres.takeIf { it.isNotEmpty() }?.joinToString(", ")),
    SongInfoRow(R.string.song_info_path, displayPath),
    SongInfoRow(R.string.song_info_mime_type, mimeType),
    SongInfoRow(R.string.song_info_size, String.format(Locale.getDefault(), "%.2f MB", size / 1024f / 1024f)),
    SongInfoRow(R.string.song_info_bit_rate, bitRate?.let { "$it kb/s" }),
    SongInfoRow(R.string.song_info_bit_depth, bitDepth?.let { "$it-bit" }),
    SongInfoRow(R.string.song_info_sample_rate, sampleRate?.let(::formatSampleRate)),
    SongInfoRow(R.string.song_info_channel_count, channelCount?.toString()),
    SongInfoRow(R.string.song_info_replay_gain_track, replayGainTrack?.let(::formatGain)),
    SongInfoRow(R.string.song_info_replay_gain_album, replayGainAlbum?.let(::formatGain)),
    SongInfoRow(R.string.song_info_lyrics, lyrics),
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

internal fun formatGain(db: Double): String = String.format(Locale.getDefault(), "%+.2f dB", db)
