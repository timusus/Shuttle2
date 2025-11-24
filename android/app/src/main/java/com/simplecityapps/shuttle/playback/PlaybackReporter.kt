package com.simplecityapps.shuttle.playback

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.emby.http.PlaybackReportingService as EmbyPlaybackReportingService
import com.simplecityapps.provider.emby.http.playbackProgress as embyPlaybackProgress
import com.simplecityapps.provider.emby.http.playbackStart as embyPlaybackStart
import com.simplecityapps.provider.emby.http.playbackStopped as embyPlaybackStopped
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.http.PlaybackReportingService as JellyfinPlaybackReportingService
import com.simplecityapps.provider.jellyfin.http.playbackProgress as jellyfinPlaybackProgress
import com.simplecityapps.provider.jellyfin.http.playbackStart as jellyfinPlaybackStart
import com.simplecityapps.provider.jellyfin.http.playbackStopped as jellyfinPlaybackStopped
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.provider.plex.http.PlaybackReportingService as PlexPlaybackReportingService
import com.simplecityapps.provider.plex.http.PlaybackState as PlexPlaybackState
import com.simplecityapps.provider.plex.http.markPlayed as plexMarkPlayed
import com.simplecityapps.provider.plex.http.timelineUpdate as plexTimelineUpdate
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Coordinates playback reporting to remote media servers (Jellyfin, Emby, Plex).
 *
 * This class follows the Observer pattern by implementing PlaybackWatcherCallback
 * to listen for playback events and report them to the appropriate media server.
 *
 * Architecture notes:
 * - Uses dependency injection to get provider-specific services and auth managers
 * - Progress updates are sent every 10 seconds (recommended interval for server APIs)
 * - Session IDs are generated per playback session for server tracking
 * - Only reports playback for remote media providers (not local files)
 */
@Singleton
class PlaybackReporter
@Inject
constructor(
    private val jellyfinPlaybackReportingService: JellyfinPlaybackReportingService,
    private val jellyfinAuthenticationManager: JellyfinAuthenticationManager,
    private val embyPlaybackReportingService: EmbyPlaybackReportingService,
    private val embyAuthenticationManager: EmbyAuthenticationManager,
    private val plexPlaybackReportingService: PlexPlaybackReportingService,
    private val plexAuthenticationManager: PlexAuthenticationManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : PlaybackWatcherCallback {
    private var currentSong: Song? = null
    private var currentSessionId: String? = null
    private var progressReportingJob: Job? = null
    private var lastReportedPosition: Int = 0
    private var isPaused: Boolean = false

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        when (playbackState) {
            is PlaybackState.Playing -> {
                val song = playbackState.queueItem.song
                if (currentSong?.id != song.id) {
                    // New song started
                    stopProgressReporting()
                    reportPlaybackStart(song)
                    currentSong = song
                    currentSessionId = UUID.randomUUID().toString()
                    lastReportedPosition = 0
                    isPaused = false
                    startProgressReporting(song)
                } else if (isPaused) {
                    // Resuming from pause
                    isPaused = false
                    startProgressReporting(song)
                }
            }
            is PlaybackState.Paused -> {
                isPaused = true
                stopProgressReporting()
                currentSong?.let { song ->
                    reportProgress(song, playbackState.progress, isPaused = true)
                }
            }
            else -> {
                // Stopped or other states
                stopProgressReporting()
                currentSong = null
                currentSessionId = null
                lastReportedPosition = 0
                isPaused = false
            }
        }
    }

    override fun onTrackEnded(song: Song) {
        stopProgressReporting()
        reportPlaybackStopped(song, song.duration)
        reportScrobble(song)
        currentSong = null
        currentSessionId = null
        lastReportedPosition = 0
        isPaused = false
    }

    private fun startProgressReporting(song: Song) {
        progressReportingJob?.cancel()
        progressReportingJob =
            appCoroutineScope.launch {
                while (isActive) {
                    delay(10_000) // Report every 10 seconds
                    if (!isPaused) {
                        // We don't have direct access to current position here,
                        // so we rely on onProgressChanged to update it
                        reportProgress(song, lastReportedPosition, isPaused = false)
                    }
                }
            }
    }

    private fun stopProgressReporting() {
        progressReportingJob?.cancel()
        progressReportingJob = null
    }

    override fun onProgressChanged(
        position: Int,
        duration: Int,
        fromUser: Boolean
    ) {
        lastReportedPosition = position
    }

    private fun reportPlaybackStart(song: Song) {
        if (!song.mediaProvider.remote || song.externalId == null) {
            return
        }

        val sessionId = currentSessionId ?: return

        appCoroutineScope.launch {
            try {
                when (song.mediaProvider) {
                    MediaProviderType.Jellyfin -> {
                        val credentials = jellyfinAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = jellyfinAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            jellyfinPlaybackReportingService.jellyfinPlaybackStart(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                itemId = song.externalId,
                                sessionId = sessionId,
                                userId = credentials.userId
                            )
                            Timber.d("Jellyfin playback start reported for ${song.name}")
                        }
                    }
                    MediaProviderType.Emby -> {
                        val credentials = embyAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = embyAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            embyPlaybackReportingService.embyPlaybackStart(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                itemId = song.externalId,
                                sessionId = sessionId,
                                userId = credentials.userId
                            )
                            Timber.d("Emby playback start reported for ${song.name}")
                        }
                    }
                    MediaProviderType.Plex -> {
                        val credentials = plexAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = plexAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            plexPlaybackReportingService.plexTimelineUpdate(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                ratingKey = song.externalId,
                                key = "/library/metadata/${song.externalId}",
                                state = PlexPlaybackState.PLAYING,
                                positionMs = 0,
                                durationMs = song.duration.toLong()
                            )
                            Timber.d("Plex playback start reported for ${song.name}")
                        }
                    }
                    else -> {
                        // Local providers don't need reporting
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to report playback start for ${song.name}")
            }
        }
    }

    private fun reportProgress(
        song: Song,
        position: Int,
        isPaused: Boolean
    ) {
        if (!song.mediaProvider.remote || song.externalId == null) {
            return
        }

        val sessionId = currentSessionId ?: return
        val positionTicks = (position * 10_000L) // Convert milliseconds to ticks (100-nanosecond units)

        appCoroutineScope.launch {
            try {
                when (song.mediaProvider) {
                    MediaProviderType.Jellyfin -> {
                        val credentials = jellyfinAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = jellyfinAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            jellyfinPlaybackReportingService.jellyfinPlaybackProgress(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                itemId = song.externalId,
                                sessionId = sessionId,
                                positionTicks = positionTicks,
                                isPaused = isPaused
                            )
                        }
                    }
                    MediaProviderType.Emby -> {
                        val credentials = embyAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = embyAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            embyPlaybackReportingService.embyPlaybackProgress(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                itemId = song.externalId,
                                sessionId = sessionId,
                                positionTicks = positionTicks,
                                isPaused = isPaused
                            )
                        }
                    }
                    MediaProviderType.Plex -> {
                        val credentials = plexAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = plexAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            plexPlaybackReportingService.plexTimelineUpdate(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                ratingKey = song.externalId,
                                key = "/library/metadata/${song.externalId}",
                                state = if (isPaused) PlexPlaybackState.PAUSED else PlexPlaybackState.PLAYING,
                                positionMs = position.toLong(),
                                durationMs = song.duration.toLong()
                            )
                        }
                    }
                    else -> {
                        // Local providers don't need reporting
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to report playback progress for ${song.name}")
            }
        }
    }

    private fun reportPlaybackStopped(
        song: Song,
        position: Int
    ) {
        if (!song.mediaProvider.remote || song.externalId == null) {
            return
        }

        val sessionId = currentSessionId ?: return
        val positionTicks = (position * 10_000L)

        appCoroutineScope.launch {
            try {
                when (song.mediaProvider) {
                    MediaProviderType.Jellyfin -> {
                        val credentials = jellyfinAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = jellyfinAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            jellyfinPlaybackReportingService.jellyfinPlaybackStopped(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                itemId = song.externalId,
                                sessionId = sessionId,
                                positionTicks = positionTicks
                            )
                            Timber.d("Jellyfin playback stopped reported for ${song.name}")
                        }
                    }
                    MediaProviderType.Emby -> {
                        val credentials = embyAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = embyAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            embyPlaybackReportingService.embyPlaybackStopped(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                itemId = song.externalId,
                                sessionId = sessionId,
                                positionTicks = positionTicks
                            )
                            Timber.d("Emby playback stopped reported for ${song.name}")
                        }
                    }
                    MediaProviderType.Plex -> {
                        val credentials = plexAuthenticationManager.getAuthenticatedCredentials()
                        val serverUrl = plexAuthenticationManager.getAddress()
                        if (credentials != null && serverUrl != null) {
                            plexPlaybackReportingService.plexTimelineUpdate(
                                serverUrl = serverUrl,
                                token = credentials.accessToken,
                                ratingKey = song.externalId,
                                key = "/library/metadata/${song.externalId}",
                                state = PlexPlaybackState.STOPPED,
                                positionMs = position.toLong(),
                                durationMs = song.duration.toLong()
                            )
                            Timber.d("Plex playback stopped reported for ${song.name}")
                        }
                    }
                    else -> {
                        // Local providers don't need reporting
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to report playback stopped for ${song.name}")
            }
        }
    }

    private fun reportScrobble(song: Song) {
        if (song.mediaProvider != MediaProviderType.Plex || song.externalId == null) {
            return
        }

        appCoroutineScope.launch {
            try {
                val credentials = plexAuthenticationManager.getAuthenticatedCredentials()
                val serverUrl = plexAuthenticationManager.getAddress()
                if (credentials != null && serverUrl != null) {
                    plexPlaybackReportingService.plexMarkPlayed(
                        serverUrl = serverUrl,
                        token = credentials.accessToken,
                        key = song.externalId,
                        identifier = "com.plexapp.plugins.library"
                    )
                    Timber.d("Plex scrobble reported for ${song.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to report scrobble for ${song.name}")
            }
        }
    }
}
