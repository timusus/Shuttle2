package com.simplecityapps.playback.spec

import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.util.concurrent.ListenableFuture
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.chromecast.FakeSongRepository
import com.simplecityapps.playback.fakes.FakeAlbumArtistRepository
import com.simplecityapps.playback.fakes.FakeAlbumRepository
import com.simplecityapps.playback.fakes.FakePlaylistRepository
import com.simplecityapps.playback.mediasession.CarAccess
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.playback.mediasession.SHUFFLE_ALL_LOAD_WAIT_MS
import com.simplecityapps.playback.mediasession.SessionCallback
import com.simplecityapps.playback.mediasession.SessionPlayer
import com.simplecityapps.playback.mediasession.UriSongResolver
import com.simplecityapps.playback.mediasession.VoiceSearchResolver
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The media session over the real playback stack of a [PlaybackHarness], as the playback service builds it, with a
 * library of [songs], [albums] and [playlists] to browse. Tests drive it as another app does, through a
 * [MediaBrowser] connected to the session ([connect]), and observe the playback stack as the spec tests do.
 *
 * The saved queue counts as restored unless [restored] is false, as it is while the app starts. Controllers are trusted
 * (as the system, Android Auto and S2 itself are) unless [trusted] is false, as another installed app isn't.
 *
 * The browsers [connect] builds are cars (Android Auto) if [car] is true, gated by the real [CarAccess] and Pro gate
 * on [entitlement], whose trial starts as the app's does ([trialStarts] counts the starts).
 */
class SessionHarness(
    val playback: PlaybackHarness = PlaybackHarness(),
    songs: List<Song> = emptyList(),
    albums: List<Album> = emptyList(),
    playlists: Map<Playlist, List<Song>> = emptyMap(),
    restored: Boolean = true,
    trusted: Boolean = true,
    car: Boolean = false,
    /** How long a shuffle-all waits for the first song to load; tests that run the player's clock far ahead raise it. */
    shuffleAllLoadWaitMs: Long = SHUFFLE_ALL_LOAD_WAIT_MS,
    /** What the store says the user has: Pro by default. */
    val entitlement: MutableStateFlow<Entitlement> = MutableStateFlow(Entitlement.Pro(ProSource.Lifetime))
) {
    var trialStarts = 0
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val browsers = mutableListOf<MediaBrowser>()

    val session: MediaLibrarySession

    /** The requests the session and the app's own entry points (a file opened with the app, a voice search) share. */
    val playRequests: PlayRequests

    /** The session's callback, for what only the system asks it directly (the resumption controls). */
    val callback: SessionCallback

    init {
        playback.queueOperations.hasRestoredQueue = restored
        val context = playback.context
        val songRepository = FakeSongRepository(songs)
        val albumRepository = FakeAlbumRepository(albums)
        val artistRepository = FakeAlbumArtistRepository()
        val playlistRepository = FakePlaylistRepository(playlists)
        val mediaIdHelper = MediaIdHelper(context, playlistRepository, artistRepository, albumRepository, songRepository) { AlbumIndex(emptyList()) }
        playRequests =
            PlayRequests(
                context = context,
                appCoroutineScope = scope,
                playbackOperations = playback.playbackOperations,
                queueOperations = playback.queueOperations,
                mediaIdHelper = mediaIdHelper,
                uriSongResolver = UriSongResolver(context, songRepository),
                voiceSearchResolver = VoiceSearchResolver(songRepository, playlistRepository),
                songRepository = songRepository,
                shuffleAllLoadWaitMs = shuffleAllLoadWaitMs
            )
        val gate = ServerAccessGate(entitlement, startTrial = {
            val eligible = entitlement.value == Entitlement.Free(trialUsed = false)
            if (eligible) {
                trialStarts++
                entitlement.value = Entitlement.Trial(Clock.System.now() + 14.days)
            }
            eligible
        })
        val carAccess = if (car) CarAccess(gate, carPackages = setOf(context.packageName)) else CarAccess(gate)
        callback = SessionCallback(context, playRequests, mediaIdHelper, playback.queueOperations, playback.playbackPreferenceManager::nowPlaying, scope, carAccess) { trusted }
        val player = SessionPlayer(playback.appPlayer, playback.playbackOperations, playback.queueOperations, scope)
        session = MediaLibrarySession.Builder(context, player, callback).setId("session-${sessions++}").build()
        callback.launchMediaButtonUpdates(session)
        callback.launchRootRefreshes(session)
    }

    /** A browser connected to the session, as Android Auto's or a Bluetooth head unit's is, telling [listener] what the session sends it. */
    fun connect(listener: MediaBrowser.Listener = object : MediaBrowser.Listener {}): MediaBrowser = await(MediaBrowser.Builder(playback.context, session.token).setListener(listener).buildAsync()).also { browsers += it }

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
