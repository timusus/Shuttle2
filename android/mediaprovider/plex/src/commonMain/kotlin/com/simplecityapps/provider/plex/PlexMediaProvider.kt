package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.Page
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.pagedFlow
import com.simplecityapps.mediaprovider.server.withServerSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.map
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class PlexMediaProvider(
    private val strings: ServerStrings,
    private val plexStrings: PlexStrings,
    private val authenticationManager: PlexAuthenticationManager,
    private val itemsService: ItemsService
) : MediaProvider {
    override val type: MediaProviderType
        get() = MediaProviderType.Plex

    private val logger = Logger.tagged("PlexMediaProvider")

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = withServerSession(strings, authenticationManager.getAddress(), ::authenticate) { address, credentials ->
        when (val sectionsResult = authenticationManager.checkSession(credentials, itemsService.sections(url = address, token = credentials.accessToken))) {
            is NetworkResult.Success<QueryResult> -> {
                val section = sectionsResult.body.mediaContainer.directories?.firstOrNull { it.title.equals("music", true) }?.key
                if (section == null) {
                    logger.error { "Failed to find 'music' section" }
                    emit(FlowEvent.Failure(plexStrings.musicLibraryMissing))
                } else {
                    emitAll(
                        queryItems(address, credentials, section).map { event ->
                            when (event) {
                                is FlowEvent.Success -> FlowEvent.Success(event.result.map { metadata -> metadata.toSong(type) })
                                is FlowEvent.Progress -> FlowEvent.Progress(event.data)
                                is FlowEvent.Failure -> FlowEvent.Failure(event.message)
                            }
                        }
                    )
                }
            }

            is NetworkResult.Failure -> {
                logger.error(sectionsResult.error) { sectionsResult.error.userDescription() }
                emit(FlowEvent.Failure(sectionsResult.error.userDescription()))
            }
        }
    }

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flowOf(FlowEvent.Success(emptyList()))

    private suspend fun authenticate(address: String): AuthenticatedCredentials? = authenticationManager.getAuthenticatedCredentials()
        ?: authenticationManager.getLoginCredentials()
            ?.let { loginCredentials -> authenticationManager.authenticate(address, loginCredentials).getOrNull() }

    private fun queryItems(
        address: String,
        credentials: AuthenticatedCredentials,
        section: String
    ): Flow<FlowEvent<List<Metadata>, MessageProgress>> = pagedFlow(strings.queryingApi) { offset, limit ->
        authenticationManager.checkSession(
            credentials,
            itemsService.items(
                url = address,
                token = credentials.accessToken,
                section = section,
                offset = offset,
                limit = limit
            )
        ).map { it.toPage() }
    }
}

private fun QueryResult.toPage() = Page(mediaContainer.metadata.orEmpty(), mediaContainer.totalSize)

internal fun Metadata.toSong(type: MediaProviderType): Song = Song(
    id = guid.hashCode().toLong(),
    name = title,
    albumArtist = grandparentTitle,
    artists = listOfNotNull(grandparentTitle),
    album = parentTitle,
    track = index ?: 0,
    disc = parentIndex ?: 0,
    duration = duration?.toInt() ?: 0,
    date = year?.let { LocalDate(it, 1, 1) },
    genres = emptyList(),
    path = "plex://$key",
    size = media.firstOrNull()?.parts?.firstOrNull()?.size ?: 0L,
    mimeType = "Audio/*",
    // When the song was added, like the Jellyfin and Emby DateCreated; updatedAt moves on every metadata refresh
    lastModified = (addedAt ?: updatedAt)?.let { seconds -> Instant.fromEpochSeconds(seconds) },
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    externalId = media.firstOrNull()?.parts?.firstOrNull()?.key,
    mediaProvider = type,
    lyrics = null,
    grouping = null,
    bitRate = media.firstOrNull()?.bitrate,
    bitDepth = null,
    sampleRate = null,
    channelCount = media.firstOrNull()?.audioChannels,
    audioCodec = media.firstOrNull()?.audioCodec,
    // When the song was added to the server, so a fresh sign-in or re-import doesn't make the whole library new
    dateAdded = addedAt?.let { seconds -> Instant.fromEpochSeconds(seconds) }
    // No artworkVersion: Plex songs have no server artwork loader, only the S2 artwork API, whose cache is keyed by URL
)
