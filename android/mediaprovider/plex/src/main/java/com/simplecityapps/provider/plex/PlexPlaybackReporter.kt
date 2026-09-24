package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.provider.plex.http.PlaybackReportingService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Reports playback through Plex's player timeline, which drives the server's now playing. Plex
 * counts the play itself once a live session's timeline reaches 90% of the song, as it does for its
 * own players, so a scrobble on top would count it twice; [markPlayed] scrobbles only a play whose
 * report failed.
 */
class PlexPlaybackReporter
@Inject
constructor(
    private val authenticationManager: PlexAuthenticationManager,
    private val playbackReportingService: PlaybackReportingService
) : PlaybackReporter {
    override fun handles(song: Song): Boolean = song.mediaProvider == MediaProviderType.Plex

    override suspend fun start(
        session: PlaybackSession,
        positionMs: Int
    ): Boolean = timeline(session.song, State.Playing, positionMs)

    override suspend fun progress(
        session: PlaybackSession,
        positionMs: Int,
        paused: Boolean
    ): Boolean = timeline(session.song, if (paused) State.Paused else State.Playing, positionMs)

    override suspend fun stop(
        session: PlaybackSession,
        positionMs: Int
    ): Boolean = timeline(session.song, State.Stopped, positionMs)

    // Plex can't backdate a play, so a late one is counted when it's sent.
    override suspend fun markPlayed(
        song: Song,
        playedAt: Instant
    ): Boolean = scrobble(song)

    private suspend fun timeline(
        song: Song,
        state: State,
        positionMs: Int
    ): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val ratingKey = plexRatingKey(song.path) ?: return false
        return playbackReportingService.timeline(
            url = "$address/:/timeline",
            token = credentials.accessToken,
            ratingKey = ratingKey,
            key = "$METADATA_PATH$ratingKey",
            identifier = LIBRARY_IDENTIFIER,
            state = state.value,
            timeMs = positionMs,
            durationMs = song.duration
        ).isSuccessful
    }

    private suspend fun scrobble(song: Song): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val ratingKey = plexRatingKey(song.path) ?: return false
        return playbackReportingService.scrobble(
            url = "$address/:/scrobble",
            token = credentials.accessToken,
            ratingKey = ratingKey,
            identifier = LIBRARY_IDENTIFIER
        ).isSuccessful
    }

    private enum class State(val value: String) {
        Playing("playing"),
        Paused("paused"),
        Stopped("stopped")
    }

    private companion object {
        const val LIBRARY_IDENTIFIER = "com.plexapp.plugins.library"
    }
}

private const val METADATA_PATH = "/library/metadata/"

/**
 * The track's ratingKey, from a Plex song's `path` (`plex://` + its metadata key,
 * `/library/metadata/{ratingKey}`). `Song.externalId` holds the media part's key instead, which
 * the timeline and scrobble endpoints don't accept. Null for a path of any other shape.
 */
internal fun plexRatingKey(path: String): String? = path
    .removePrefix("plex://")
    .takeIf { it.startsWith(METADATA_PATH) }
    ?.removePrefix(METADATA_PATH)
    ?.substringBefore('/')
    ?.takeIf { it.isNotEmpty() }
