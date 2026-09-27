package com.simplecityapps.provider.jellyfin

import android.content.Context
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.R
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.Page
import com.simplecityapps.mediaprovider.server.ResourceServerStrings
import com.simplecityapps.mediaprovider.server.pagedFlow
import com.simplecityapps.mediaprovider.server.withServerSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.map
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.jellyfin.http.Item
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.QueryResult
import com.simplecityapps.provider.jellyfin.http.audioItems
import com.simplecityapps.provider.jellyfin.http.playlistItems
import com.simplecityapps.provider.jellyfin.http.playlists
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import timber.log.Timber

class JellyfinMediaProvider(
    private val context: Context,
    private val authenticationManager: JellyfinAuthenticationManager,
    private val itemsService: ItemsService
) : MediaProvider {
    override val type = MediaProviderType.Jellyfin

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = withServerSession(ResourceServerStrings(context), authenticationManager.getAddress(), ::authenticate) { address, credentials ->
        emitAll(
            queryItems(
                address = address,
                credentials = credentials
            ).map { event ->
                when (event) {
                    is FlowEvent.Success -> {
                        FlowEvent.Success(event.result.map { it.toSong() })
                    }

                    is FlowEvent.Progress -> {
                        FlowEvent.Progress(event.data)
                    }

                    is FlowEvent.Failure -> {
                        FlowEvent.Failure(event.message)
                    }
                }
            }
        )
    }

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = withServerSession(ResourceServerStrings(context), authenticationManager.getAddress(), ::authenticate) { address, credentials ->
        when (
            val queryResult =
                authenticationManager.checkSession(
                    credentials,
                    itemsService.playlists(
                        url = address,
                        authorization = authenticationManager.authorizationHeader(credentials),
                        userId = credentials.userId
                    )
                )
        ) {
            is NetworkResult.Success<QueryResult> -> {
                val updateData = findSongsForPlaylists(address, credentials, queryResult.body.items, existingSongs).toList()
                emit(FlowEvent.Success(updateData))
            }

            is NetworkResult.Failure -> {
                Timber.e(queryResult.error, queryResult.error.userDescription())
                emit(FlowEvent.Failure(queryResult.error.userDescription()))
            }
        }
    }

    private suspend fun authenticate(address: String): AuthenticatedCredentials? = authenticationManager.getAuthenticatedCredentials()
        ?.let { cachedCredentials -> authenticationManager.refreshDownloadPermission(address, cachedCredentials) }
        ?: authenticationManager.getLoginCredentials()
            ?.let { loginCredentials ->
                authenticationManager.authenticate(
                    address,
                    loginCredentials
                ).getOrNull()
            }

    private fun queryItems(
        address: String,
        credentials: AuthenticatedCredentials
    ): Flow<FlowEvent<List<Item>, MessageProgress>> = pagedFlow(context.getString(R.string.media_provider_querying_api)) { offset, limit ->
        authenticationManager.checkSession(
            credentials,
            itemsService.audioItems(
                url = address,
                authorization = authenticationManager.authorizationHeader(credentials),
                userId = credentials.userId,
                limit = limit,
                startIndex = offset
            )
        ).map { it.toPage() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun findSongsForPlaylists(
        address: String,
        credentials: AuthenticatedCredentials,
        playlistItems: List<Item>,
        existingSongs: List<Song>
    ): Flow<MediaImporter.PlaylistUpdateData> = playlistItems
        .asFlow()
        .flatMapConcat { playlistItem ->
            queryPlaylistItems(address, credentials, playlistItem.id)
                .map { event ->
                    when (event) {
                        is FlowEvent.Success -> {
                            MediaImporter.PlaylistUpdateData(
                                mediaProviderType = type,
                                name = playlistItem.name ?: context.getString(com.simplecityapps.core.R.string.unknown),
                                songs = event.result.mapNotNull { item -> existingSongs.firstOrNull { it.externalId == item.id } },
                                externalId = playlistItem.id
                            )
                        }

                        is FlowEvent.Failure -> null

                        is FlowEvent.Progress -> null
                    }
                }
        }
        .filterNotNull()

    private fun queryPlaylistItems(
        address: String,
        credentials: AuthenticatedCredentials,
        playlistId: String
    ): Flow<FlowEvent<List<Item>, MessageProgress>> = pagedFlow(context.getString(R.string.media_provider_querying_api)) { offset, limit ->
        authenticationManager.checkSession(
            credentials,
            itemsService.playlistItems(
                url = address,
                authorization = authenticationManager.authorizationHeader(credentials),
                playlistId = playlistId,
                limit = limit,
                startIndex = offset,
                userId = credentials.userId
            )
        ).map { it.toPage() }
    }
}

private fun QueryResult.toPage() = Page(items, totalRecordCount)

internal fun Item.toSong(): Song = Song(
    id = 0,
    name = name,
    albumArtist = albumArtist,
    artists = artists.filter { it.isNotEmpty() },
    album = album,
    track = indexNumber,
    disc = parentIndexNumber,
    duration = ((runTime ?: 0) / (10 * 1000)).toInt(),
    date = productionYear?.let { year -> LocalDate(year, 1, 1) },
    genres = genres,
    path = "jellyfin://item/$id",
    size = 0,
    mimeType = "Audio/*",
    // The server has no modified date for items; DateCreated (when the song was added) is the closest
    lastModified = dateCreated?.let { date -> runCatching { Instant.parse(date) }.getOrNull() },
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    externalId = id,
    mediaProvider = MediaProviderType.Jellyfin,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
    // Artwork for songs and albums is the album's primary image
    artworkVersion = albumPrimaryImageTag
)
