package com.simplecityapps.shuttle.shared.intents

import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.screens.search.SearchCategory
import com.simplecityapps.shuttle.ui.screens.search.SearchLibrary
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/** What a voice request names: Siri's INMediaItem types. */
enum class VoiceMediaKind { Artist, Album, Song, Playlist, Genre }

/**
 * One library item a spoken request matched. [artist] says whose album or
 * song it is. Swift turns it into an `INMediaItem`, and hands it back to [VoiceLibrary.play].
 */
class VoiceMatch internal constructor(
    val kind: VoiceMediaKind,
    val title: String,
    val artist: String?,
    internal val selection: MediaSelection,
)

/** What [VoiceLibrary.state] reports: enough to tell that the library changed. */
data class VoiceLibraryState(val songCount: Int, val playlistCount: Int, val artistCount: Int)

/**
 * What Siri's media domain (`INPlayMediaIntent`, #951) asks of the library: matches for a spoken name through the same
 * search as the Search screen, the plays Swift's `SiriMediaResolver` dispatches, and the counts and names it tells Siri
 * about. Built here so Swift never spells out a query, a selection or a ranking.
 */
@Inject
class VoiceLibrary(
    private val searchLibrary: SearchLibrary,
    private val observeAlbums: ObserveAlbums,
    private val observeArtists: ObserveArtists,
    private val observePlaylists: ObservePlaylists,
) {
    /**
     * The library items of [kinds] matching [query], best first: the kind of the best hit overall leads, within a kind
     * the search's order stands. Empty for a blank [query].
     */
    suspend fun search(query: String, kinds: List<VoiceMediaKind>, limit: Int): List<VoiceMatch> {
        val categories = kinds.map { it.category }.toSet()
        val results = searchLibrary(query, categories).first()
        val byKind = mapOf(
            VoiceMediaKind.Artist to results.artists.map { it.item }.map { artist ->
                VoiceMatch(VoiceMediaKind.Artist, artist.friendlyArtistName ?: artist.name.orEmpty(), null, MediaSelection.AlbumArtists(artist))
            },
            VoiceMediaKind.Album to results.albums.map { it.item }.map { album ->
                VoiceMatch(VoiceMediaKind.Album, album.name.orEmpty(), album.friendlyArtistName, MediaSelection.Albums(album))
            },
            VoiceMediaKind.Song to results.songs.map { it.item }.map { song ->
                VoiceMatch(VoiceMediaKind.Song, song.name.orEmpty(), song.albumArtist ?: song.artists.firstOrNull(), MediaSelection.Songs(song))
            },
            VoiceMediaKind.Playlist to results.playlists.map { it.item }.map { playlist ->
                VoiceMatch(VoiceMediaKind.Playlist, playlist.name, null, MediaSelection.Playlists(playlist))
            },
            VoiceMediaKind.Genre to results.genres.map { it.item }.map { genre ->
                VoiceMatch(VoiceMediaKind.Genre, genre.name, null, MediaSelection.Genres(genre))
            },
        )
        val lead = results.top?.kind
        return byKind.entries
            .sortedBy { if (it.key == lead) 0 else 1 }
            .flatMap { it.value }
            .take(limit)
    }

    /** Plays [match], or shuffles it. */
    fun play(match: VoiceMatch, shuffled: Boolean): MediaAction = if (shuffled) MediaAction.Shuffle(match.selection) else MediaAction.Play(match.selection)

    /**
     * How the library stands, again whenever it changes, so Siri's context and vocabulary follow it. The song count is
     * the albums' counts summed, so no song list is loaded.
     */
    fun state(): Flow<VoiceLibraryState> = combine(observeAlbums(), observePlaylists(), observeArtists()) { albums, playlists, artists ->
        VoiceLibraryState(albums.sumOf { it.songCount }, playlists.size, artists.size)
    }.distinctUntilChanged()

    /** The names of the most played artists, most first, for Siri's vocabulary. */
    suspend fun topArtistNames(limit: Int): List<String> = observeArtists().first()
        .sortedByDescending { it.playCount }
        .mapNotNull { it.friendlyArtistName ?: it.name }
        .take(limit)

    /** The names of the playlists, biggest first (they carry no play count), for Siri's vocabulary. */
    suspend fun topPlaylistNames(limit: Int): List<String> = observePlaylists().first()
        .sortedByDescending { it.songCount }
        .map { it.name }
        .take(limit)

    private val VoiceMediaKind.category: SearchCategory
        get() = when (this) {
            VoiceMediaKind.Artist -> SearchCategory.Artists
            VoiceMediaKind.Album -> SearchCategory.Albums
            VoiceMediaKind.Song -> SearchCategory.Songs
            VoiceMediaKind.Playlist -> SearchCategory.Playlists
            VoiceMediaKind.Genre -> SearchCategory.Genres
        }

    private val SearchCategory.kind: VoiceMediaKind
        get() = when (this) {
            SearchCategory.Artists -> VoiceMediaKind.Artist
            SearchCategory.Albums -> VoiceMediaKind.Album
            SearchCategory.Songs -> VoiceMediaKind.Song
            SearchCategory.Playlists -> VoiceMediaKind.Playlist
            SearchCategory.Genres -> VoiceMediaKind.Genre
        }
}
