package com.simplecityapps.shuttle.ui.text

import androidx.annotation.StringRes
import com.simplecityapps.shuttle.R

@get:StringRes
val SongInfoString.resId: Int
    get() = when (this) {
        SongInfoString.SONG_INFO_SECTION_TAGS -> R.string.song_info_section_tags
        SongInfoString.SONG_INFO_SECTION_FILE -> R.string.song_info_section_file
        SongInfoString.SONG_INFO_SECTION_PLAYBACK -> R.string.song_info_section_playback
        SongInfoString.SONG_INFO_TRACK_TITLE -> R.string.song_info_track_title
        SongInfoString.SONG_INFO_ARTISTS -> R.string.song_info_artists
        SongInfoString.SONG_INFO_ALBUM -> R.string.song_info_album
        SongInfoString.SONG_INFO_ALBUM_ARTIST -> R.string.song_info_album_artist
        SongInfoString.SONG_INFO_YEAR -> R.string.song_info_year
        SongInfoString.SONG_INFO_TRACK_NUMBER -> R.string.song_info_track_number
        SongInfoString.SONG_INFO_DISC -> R.string.song_info_disc
        SongInfoString.SONG_INFO_GENRES -> R.string.song_info_genres
        SongInfoString.SONG_INFO_LYRICS -> R.string.song_info_lyrics
        SongInfoString.SONG_INFO_SOURCE -> R.string.song_info_source
        SongInfoString.SONG_INFO_SOURCE_THIS_DEVICE -> R.string.song_info_source_this_device
        SongInfoString.SONG_INFO_PATH -> R.string.song_info_path
        SongInfoString.SONG_INFO_MIME_TYPE -> R.string.song_info_mime_type
        SongInfoString.SONG_INFO_SIZE -> R.string.song_info_size
        SongInfoString.SONG_INFO_DURATION -> R.string.song_info_duration
        SongInfoString.SONG_INFO_BIT_RATE -> R.string.song_info_bit_rate
        SongInfoString.SONG_INFO_BIT_DEPTH -> R.string.song_info_bit_depth
        SongInfoString.SONG_INFO_SAMPLE_RATE -> R.string.song_info_sample_rate
        SongInfoString.SONG_INFO_CHANNEL_COUNT -> R.string.song_info_channel_count
        SongInfoString.SONG_INFO_PLAY_COUNT -> R.string.song_info_play_count
        SongInfoString.SONG_INFO_REPLAY_GAIN_TRACK -> R.string.song_info_replay_gain_track
        SongInfoString.SONG_INFO_REPLAY_GAIN_ALBUM -> R.string.song_info_replay_gain_album
    }
