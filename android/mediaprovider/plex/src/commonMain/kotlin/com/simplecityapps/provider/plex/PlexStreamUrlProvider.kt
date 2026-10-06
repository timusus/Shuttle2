package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.TranscodeCodec
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.plex.http.TranscodeService
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.KeyValueStore
import dev.zacsweers.metro.Inject
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * A stream URL and the MIME type it serves. [isTranscode] when it's a transcode, which the server drops once another
 * one starts for the user (see [PlexStreamUrlProvider]).
 */
data class PlexStream(
    val path: String,
    val mimeType: String,
    val isTranscode: Boolean = false
)

/**
 * A Plex song's authenticated stream: the original part file when it's a format this platform's player decodes
 * ([StreamProfile.directPlayFormats]) and there's no [StreamingPolicy] or the song's bitrate is known to be within
 * it; otherwise a transcode at the cap, or at [UNCAPPED_TRANSCODE_KBPS] without one. An unknown bitrate transcodes, as
 * Jellyfin does. Plex, unlike Jellyfin and Emby's universal endpoint, serves a part file as it is, so a format the
 * player can't decode (WMA, or ALAC inside an otherwise-playable container on Android) would fail to load and be
 * skipped (#362, #567).
 *
 * The transcode follows [StreamProfile.playsHls]: an HLS stream where the player plays HLS (Android's Media3), which
 * stays seekable, in the chosen codec ([StreamingPolicy.transcodeFormat]: AAC or MP3; HLS carries no Opus, so
 * [StreamProfile.streamTarget] falls it back to AAC); otherwise one progressive MP3 (iOS's engine), which starts at the
 * position it's asked for. MP3 is the one progressive target Plex's transcoder reliably serves, so a progressive
 * transcode stays MP3 whatever the choice.
 *
 * Plex keeps one transcode running for the user: any start replaces it (another song's, or another position's), except
 * a start on the same session at the start of the song, which joins it. And it answers 400 to a start whose
 * `X-Plex-Session-Identifier` isn't the last one it saw for the track, until another track is transcoded (#888;
 * support/scripts/plex-transcode-probe.sh). So every transcode of a song, streamed or downloaded, carries one
 * identifier, [sessionIdentifier], and plays of a song share one transcode session while any of them holds it: a
 * replay, or repeat one's next opened while the song plays, joins the transcode the song is playing from rather than
 * cutting it off. A seek's re-open replaces it at the position.
 *
 * [endPlay] stops a progressive transcode (#722) once the last play holding its session ends, and retires the session
 * in the same step, so a play opened after that gets a new one the stop can't reach; the server otherwise keeps
 * transcoding a skipped song until it times out idle. A stream with no play (Android's HLS, which the server times out
 * once its segments stop being read) is on the song's own session, which nothing stops. The open sessions, and the
 * retired ones not yet stopped, are also kept in [store], so the ones a killed app left running (or a stop that failed)
 * are stopped at the next launch ([stopLeftoverSessions]). Each run's sessions carry its own [run] id, so a session
 * this run opens is never one a previous run left.
 */
@Inject
class PlexStreamUrlProvider(
    private val authenticationManager: PlexAuthenticationManager,
    private val streamingPolicy: StreamingPolicy,
    private val streamProfile: StreamProfile,
    private val transcodeService: TranscodeService,
    private val store: KeyValueStore
) : StreamUrlProvider {
    private val logger = Logger.tagged("PlexStreamUrlProvider")

    /** Guards [transcodeSessions] and its copy in [store], so concurrent changes are each written with the other. */
    private val lock = SynchronizedObject()

    private var transcodeSessions = TranscodeSessions(
        unstopped = store.getString(OPEN_SESSIONS_KEY, null)?.split(SESSION_SEPARATOR)?.filter(String::isNotEmpty)?.toSet().orEmpty()
    )

    /** Part of every session this run opens, telling them from the ones a previous run left. */
    private val run = Uuid.random().toHexString().take(8)

    /**
     * The session each play's progressive transcode was opened on, the one each song's plays share now, and the
     * sessions no play holds that haven't been stopped yet: a previous run's, or one whose stop failed.
     */
    private data class TranscodeSessions(
        val byPlay: Map<String, String> = emptyMap(),
        val bySong: Map<String, String> = emptyMap(),
        val opened: Int = 0,
        val unstopped: Set<String> = emptySet()
    ) {
        /** What [store] keeps: every session that may still be running on the server. */
        val running: Set<String> get() = byPlay.values.toSet() + unstopped
    }

    /** Applies [change] to the sessions and writes them to [store] in one step. */
    private fun updateSessions(change: (TranscodeSessions) -> TranscodeSessions) = synchronized(lock) {
        val updated = change(transcodeSessions)
        val running = updated.running
        if (running != transcodeSessions.running) {
            store.edit { putString(OPEN_SESSIONS_KEY, running.takeIf { it.isNotEmpty() }?.joinToString(SESSION_SEPARATOR)) }
        }
        transcodeSessions = updated
    }

    override fun handles(scheme: String?): Boolean = scheme == "plex"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long,
        playId: String?
    ): String = stream(song, startPositionMs, playId).path

    /**
     * Stops [playId]'s transcode session, if a progressive transcode was opened for it and no other play holds it.
     * Best effort: a session that can't be stopped now is kept, and stopped at the next launch.
     */
    override suspend fun endPlay(playId: String) {
        var retired: String? = null
        updateSessions { sessions ->
            val session = sessions.byPlay[playId]
            val byPlay = sessions.byPlay - playId
            retired = session?.takeIf { it !in byPlay.values }
            sessions.copy(
                byPlay = byPlay,
                bySong = sessions.bySong.filterValues { it != retired },
                unstopped = sessions.unstopped + listOfNotNull(retired)
            )
        }
        val session = retired ?: return
        if (stop(session)) updateSessions { it.copy(unstopped = it.unstopped - session) }
    }

    /**
     * Stops the sessions a previous run left open (the app was killed with a transcode running, or a stop failed),
     * forgetting each once it's stopped. Off the launch path; one that can't be stopped now (signed out, offline) is
     * kept for the next launch. A session a play of this run holds is never stopped.
     */
    suspend fun stopLeftoverSessions() {
        val leftovers = synchronized(lock) { transcodeSessions.unstopped }
        leftovers.forEach { session ->
            val held = synchronized(lock) { session in transcodeSessions.byPlay.values }
            if (held || stop(session)) updateSessions { it.copy(unstopped = it.unstopped - session) }
        }
    }

    /** Whether [session] is stopped: the server stopped it, or answered that it's gone (404). */
    private suspend fun stop(session: String): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val result = transcodeService.stop(url = "$address$TRANSCODE_STOP_PATH", token = credentials.accessToken, session = session)
        if (result !is NetworkResult.Failure) return true
        if ((result.error as? RemoteServiceHttpError)?.httpStatusCode == HttpStatusCode.NotFound) return true
        logger.warn { "Failed to stop transcode session $session: ${result.error}" }
        return false
    }

    /** @throws IllegalStateException when the server isn't signed in to, or the URL can't be built. */
    fun stream(
        song: Song,
        startPositionMs: Long = 0,
        playId: String? = null
    ): PlexStream {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        val maxBitrateKbps = streamingPolicy.maxBitrateKbps()
        val bitRate = song.bitRate
        val withinCap = maxBitrateKbps == null || (bitRate != null && bitRate <= maxBitrateKbps)
        if (withinCap && isDecodable(song)) {
            val path = authenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials)
                ?: throw IllegalStateException("Failed to build plex path")
            streamingPolicy.streamOpened(song.path, null)
            return PlexStream(path, song.mimeType)
        }
        val transcodeKbps = maxBitrateKbps ?: UNCAPPED_TRANSCODE_KBPS
        return if (streamProfile.playsHls) {
            val codec = hlsCodec()
            val path = authenticationManager.buildPlexTranscodePath(song, authenticatedCredentials, transcodeKbps, sessionIdentifier(song), codec)
                ?: throw IllegalStateException("Failed to build plex transcode path")
            streamingPolicy.streamOpened(song.path, codec.delivered(transcodeKbps))
            PlexStream(path, HLS_MIME_TYPE, isTranscode = true)
        } else {
            val session = if (playId == null) sessionIdentifier(song) else playSession(song, playId)
            val path = authenticationManager.buildPlexProgressiveStreamPath(
                song,
                authenticatedCredentials,
                transcodeKbps,
                startPositionMs,
                session = session,
                sessionIdentifier = sessionIdentifier(song)
            ) ?: throw IllegalStateException("Failed to build plex transcode path")
            streamingPolicy.streamOpened(song.path, TranscodeCodec.Mp3.delivered(transcodeKbps))
            PlexStream(path, PROGRESSIVE_TRANSCODE_MIME_TYPE, isTranscode = true)
        }
    }

    /**
     * The session [playId]'s transcode runs on: the one it was opened on, else the one [song]'s plays share now, else a
     * new one, which its plays share from then on.
     */
    private fun playSession(
        song: Song,
        playId: String
    ): String {
        val identifier = sessionIdentifier(song)
        var session = ""
        updateSessions { sessions ->
            val shared = sessions.byPlay[playId] ?: sessions.bySong[identifier]
            session = shared ?: "$identifier-$run-${sessions.opened + 1}"
            sessions.copy(
                byPlay = sessions.byPlay + (playId to session),
                bySong = sessions.bySong + (identifier to session),
                opened = if (shared == null) sessions.opened + 1 else sessions.opened
            )
        }
        return session
    }

    /** The codec an HLS transcode is in: the chosen one, Opus falling back to AAC. */
    private fun hlsCodec(): TranscodeCodec = streamProfile.streamTarget(streamingPolicy.transcodeFormat()).codec

    /** The `X-Plex-Session-Identifier` every transcode of [song] carries, and the session of a stream with no play. */
    private fun sessionIdentifier(song: Song): String = "s2-${plexRatingKey(song.path) ?: song.path}"

    /**
     * The original part file (`song.externalId`) when the player decodes it and it's within the download cap
     * ([StreamingPolicy.downloadMaxBitrateKbps]); otherwise a progressive MP3 transcode at the cap, or at
     * [UNCAPPED_TRANSCODE_KBPS] without one, so the download is one file of real, playable audio recorded under the type
     * it actually is: not the HLS transcode Android streams, whose manifest would be saved as the "file" (#567).
     */
    override fun downloadSource(song: Song): DownloadSource? {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        val maxBitrateKbps = streamingPolicy.downloadMaxBitrateKbps()
        val bitRate = song.bitRate
        val withinCap = maxBitrateKbps == null || (bitRate != null && bitRate <= maxBitrateKbps)
        return if (withinCap && isDecodable(song)) {
            val path = authenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials) ?: return null
            DownloadSource(path, song.mimeType)
        } else {
            val path = authenticationManager.buildPlexProgressiveTranscodePath(
                song,
                authenticatedCredentials,
                maxBitrateKbps ?: UNCAPPED_TRANSCODE_KBPS,
                sessionIdentifier(song)
            ) ?: return null
            DownloadSource(path, PROGRESSIVE_TRANSCODE_MIME_TYPE)
        }
    }

    /**
     * Whether the player decodes [song]'s part as it is. The container comes from the part's file extension (Plex
     * names a part's file after it: `/library/parts/{id}/{updatedAt}/file.{container}`).
     */
    fun isDecodable(song: Song): Boolean = streamProfile.directPlayFormats.isDecodable(
        container = song.externalId?.substringAfterLast('/')?.substringAfterLast('.', missingDelimiterValue = ""),
        audioCodec = song.audioCodec
    )

    companion object {
        const val HLS_MIME_TYPE = "application/x-mpegURL"
        const val PROGRESSIVE_TRANSCODE_MIME_TYPE = "audio/mpeg"

        /** The bitrate a format the player can't decode is transcoded to when streaming isn't capped. */
        const val UNCAPPED_TRANSCODE_KBPS = 320

        /** Plex's universal transcoder takes a stop for any media type at its video path. */
        private const val TRANSCODE_STOP_PATH = "/video/:/transcode/universal/stop"

        private const val OPEN_SESSIONS_KEY = "plex_open_transcode_sessions"
        private const val SESSION_SEPARATOR = ","
    }
}
