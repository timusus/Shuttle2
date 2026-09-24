package com.simplecityapps.playback.spec

import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.util.concurrent.ListenableFuture
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.chromecast.FakeSongRepository
import com.simplecityapps.playback.fakes.FakeAlbumArtistRepository
import com.simplecityapps.playback.fakes.FakeAlbumRepository
import com.simplecityapps.playback.fakes.FakeGenreRepository
import com.simplecityapps.playback.fakes.FakePlaylistRepository
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.playback.mediasession.SessionCallback
import com.simplecityapps.playback.mediasession.SessionPlayer
import com.simplecityapps.playback.mediasession.UriSongResolver
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The media session over the real playback stack of a [PlaybackHarness], as the playback service builds it, with a
 * library of [songs], [albums] and [playlists] to browse. Tests drive it as another app does, through a
 * [MediaBrowser] connected to the session ([connect]), and observe the playback stack as the spec tests do.
 *
 * The saved queue counts as restored unless [restored] is false, as it is while the app starts. Controllers are trusted
 * (as the system, Android Auto and S2 itself are) unless [trusted] is false, as another installed app isn't.
 */
class SessionHarness(
    val playback: PlaybackHarness = PlaybackHarness(),
    songs: List<Song> = emptyList(),
    albums: List<Album> = emptyList(),
    playlists: Map<Playlist, List<Song>> = emptyMap(),
    restored: Boolean = true,
    trusted: Boolean = true
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val browsers = mutableListOf<MediaBrowser>()

    val session: MediaLibrarySession

    init {
        playback.queueOperations.hasRestoredQueue = restored
        val context = playback.context
        val songRepository = FakeSongRepository(songs)
        val albumRepository = FakeAlbumRepository(albums)
        val artistRepository = FakeAlbumArtistRepository()
        val mediaIdHelper = MediaIdHelper(FakePlaylistRepository(playlists), artistRepository, albumRepository, songRepository)
        val playRequests =
            PlayRequests(
                context = context,
                appCoroutineScope = scope,
                playbackOperations = playback.playbackOperations,
                queueOperations = playback.queueOperations,
                mediaIdHelper = mediaIdHelper,
                uriSongResolver = UriSongResolver(context, songRepository),
                artistRepository = artistRepository,
                albumRepository = albumRepository,
                songRepository = songRepository,
                genreRepository = FakeGenreRepository()
            )
        val callback = SessionCallback(context, playRequests, mediaIdHelper, playback.queueOperations, scope) { trusted }
        val player = SessionPlayer(playback.appPlayer, playback.playbackOperations, playback.queueOperations, scope)
        session = MediaLibrarySession.Builder(context, player, callback).setId("session-${sessions++}").build()
        callback.launchMediaButtonUpdates(session)
    }

    /** A browser connected to the session, as Android Auto's or a Bluetooth head unit's is. */
    fun connect(): MediaBrowser = await(MediaBrowser.Builder(playback.context, session.token).buildAsync()).also { browsers += it }

    /** Turns the main looper, where the session and the playback stack live, until [future] is done. */
    fun <T> await(future: ListenableFuture<T>): T {
        playback.runUntil { future.isDone }
        return future.get()
    }

    fun release() {
        browsers.forEach(MediaBrowser::release)
        session.release()
        scope.cancel()
        playback.release()
    }

    private companion object {
        // A session id is unique within the process.
        var sessions = 0
    }
}
