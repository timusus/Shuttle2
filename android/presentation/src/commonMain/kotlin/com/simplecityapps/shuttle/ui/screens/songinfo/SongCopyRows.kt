package com.simplecityapps.shuttle.ui.screens.songinfo

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PreferredCopy
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.copyAlbumKey
import com.simplecityapps.shuttle.model.copyKey
import com.simplecityapps.shuttle.ui.actions.CopyContext

/** The pill on a copy. Unplayable reasons come first: a copy that can't play is never "Playing". */
enum class SongCopyState {
    /** The copy that plays now. */
    Playing,

    /** Made primary by the user. */
    Primary,
    Downloaded,
    Unreachable,
    Missing,
    Offline,

    /** Plays if asked, with nothing to say about it. */
    Available,
}

/** How the playing copy was chosen, for the footnote. */
sealed interface CopyChoice {
    data object Primary : CopyChoice
    data object AlbumPrimary : CopyChoice
    data class Automatic(val preferred: PreferredCopy) : CopyChoice
}

data class SongCopyRow(
    /** The copy itself, for Play this copy, Make primary and the like. */
    val song: Song,
    val provider: MediaProviderType,
    val format: String?,
    val bitRate: String?,
    val bitDepth: String?,
    val sampleRate: String?,
    val state: SongCopyState,
    /** Made primary by the user, whatever [state] shows: its menu offers "Use automatic choice". */
    val primary: Boolean,
    val playable: Boolean,
)

/** A merged song's copies, best first, as Song Info lists them. */
data class SongCopyRows(
    val rows: List<SongCopyRow>,
    val chosenBy: CopyChoice,
    val sourceCount: Int,
)

/** The rows for [this] song's copies; null unless the library merges and the song has two or more. */
fun Song.copyRows(context: CopyContext): SongCopyRows? {
    if (!context.mergeCopies || copies.size < 2) return null
    val availability = context.availability
    val preferences = context.preferences
    val rows = copies.mapIndexed { index, copy ->
        val primary = copy.copyKey in preferences.primaryCopies
        val state = when {
            copy.id in availability.missingSongIds -> SongCopyState.Missing
            availability.offline && !availability.isOnDevice(copy) -> SongCopyState.Offline
            !availability.isPlayable(copy) -> SongCopyState.Unreachable
            index == 0 -> SongCopyState.Playing
            primary -> SongCopyState.Primary
            copy.mediaProvider.remote && availability.isOnDevice(copy) -> SongCopyState.Downloaded
            else -> SongCopyState.Available
        }
        SongCopyRow(
            song = copy,
            provider = copy.mediaProvider,
            format = copy.formatLabel(),
            bitRate = copy.bitRate?.let(::formatBitRate),
            bitDepth = copy.bitDepth?.let { "$it-bit" },
            sampleRate = copy.sampleRate?.let(::formatSampleRate),
            state = state,
            primary = primary,
            playable = availability.isPlayable(copy),
        )
    }
    val playing = copies.first()
    val chosenBy = when {
        playing.copyKey in preferences.primaryCopies -> CopyChoice.Primary
        playing.copyAlbumKey in preferences.primaryAlbums -> CopyChoice.AlbumPrimary
        else -> CopyChoice.Automatic(preferences.preferred)
    }
    return SongCopyRows(rows, chosenBy, sourceCount = copies.map { it.mediaProvider }.distinct().size)
}
