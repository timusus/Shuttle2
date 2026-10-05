package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.losslessBitDepth
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.Page
import com.simplecityapps.mediaprovider.server.ServerSession
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.mediaprovider.server.pagedFlow
import com.simplecityapps.mediaprovider.server.withServerSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.map
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.subsonic.http.AlbumDto
import com.simplecityapps.provider.subsonic.http.ArtistRefDto
import com.simplecityapps.provider.subsonic.http.ReplayGainDto
import com.simplecityapps.provider.subsonic.http.SongDto
import com.simplecityapps.provider.subsonic.http.SubsonicService
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.musicBrainzIds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.datetime.LocalDate

/**
 * Syncs a Subsonic server's songs and playlists. Songs come from `search3` with an empty query, a page at a time, which
 * Navidrome and other OpenSubsonic servers answer with every song. A server that isn't OpenSubsonic and answers it with
 * nothing is read album by album instead (`getAlbumList2`, then `getAlbum` for each).
 */
class SubsonicMediaProvider(
    private val strings: ServerStrings,
    private val authenticationManager: SubsonicAuthenticationManager,
    private val service: SubsonicService
) : MediaProvider {
    private val logger = Logger.tagged("SubsonicMediaProvider")

    override val type = MediaProviderType.Subsonic

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
        val searched = forwardingProgress(
            pagedFlow(key = SongDto::id) { offset, limit ->
                session.request { credentials -> authenticationManager.request(credentials) { auth -> service.songs(address, auth, offset, limit) } }
                    .map { songs -> Page(songs, totalCount = null) }
            }
        ) ?: return@withServerSession
        val songs = if (searched.result.isEmpty() && authenticationManager.serverInfo?.openSubsonic != true) {
            logger.info { "search3 found no songs; reading the library album by album" }
            songsByAlbum(address, session) ?: return@withServerSession
        } else {
            searched.result
        }
        emit(FlowEvent.Success(songs.filter(SongDto::isSong).map(SongDto::toSong), searched.missing))
    }

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = withServerSession(strings, authenticationManager.credentialStore, authenticationManager.getAddress(), ::authenticate) { address, session ->
        val playlists = when (val result = session.request { credentials -> authenticationManager.request(credentials) { auth -> service.playlists(address, auth) } }) {
            is NetworkResult.Success -> result.body
            is NetworkResult.Failure -> return@withServerSession fail(result.error)
        }
        val songsById = existingSongs.filter { it.mediaProvider == type }.associateBy { it.externalId }
        val updates = playlists.mapNotNull { playlist ->
            when (val result = session.request { credentials -> authenticationManager.request(credentials) { auth -> service.playlist(address, auth, playlist.id) } }) {
                is NetworkResult.Success -> MediaImporter.PlaylistUpdateData(
                    mediaProviderType = type,
                    name = playlist.name ?: strings.unknownName,
                    songs = result.body.entry.mapNotNull { entry -> songsById[entry.id] },
                    externalId = playlist.id
                )

                is NetworkResult.Failure -> {
                    logger.warn(result.error) { "Failed to read playlist ${playlist.id}" }
                    null
                }
            }
        }
        emit(FlowEvent.Success(updates))
    }

    /** The stored credentials, or a fresh sign-in with the saved login when there are none (after a 401 signed out). */
    private suspend fun authenticate(address: String): AuthenticatedCredentials? = authenticationManager.getAuthenticatedCredentials()
        ?: authenticationManager.getLoginCredentials()?.let { login -> authenticationManager.authenticate(address, login).getOrNull() }

    /** Every song, read album by album, emitting progress as it goes; null after emitting a failure. */
    private suspend fun FlowCollector<FlowEvent<List<Song>, MessageProgress>>.songsByAlbum(
        address: String,
        session: ServerSession<AuthenticatedCredentials>
    ): List<SongDto>? {
        val albums = forwardingProgress(
            pagedFlow(key = AlbumDto::id) { offset, limit ->
                session.request { credentials -> authenticationManager.request(credentials) { auth -> service.albums(address, auth, offset, limit) } }
                    .map { albums -> Page(albums, totalCount = null) }
            }
        )?.result ?: return null
        val songs = mutableListOf<SongDto>()
        for ((index, album) in albums.withIndex()) {
            emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, Progress(index + 1, albums.size))))
            when (val result = session.request { credentials -> authenticationManager.request(credentials) { auth -> service.album(address, auth, album.id) } }) {
                is NetworkResult.Success -> songs += result.body.song
                is NetworkResult.Failure -> return null.also { fail(result.error) }
            }
        }
        return songs.distinctBy(SongDto::id)
    }

    /** Emits [flow]'s progress and failure, and returns its success; null when it failed. */
    private suspend fun <T> FlowCollector<FlowEvent<List<Song>, MessageProgress>>.forwardingProgress(
        flow: Flow<FlowEvent<List<T>, MessageProgress>>
    ): FlowEvent.Success<List<T>>? {
        var success: FlowEvent.Success<List<T>>? = null
        flow.collect { event ->
            when (event) {
                is FlowEvent.Progress -> emit(FlowEvent.Progress(event.data))
                is FlowEvent.Failure -> emit(event)
                is FlowEvent.Success -> success = event
            }
        }
        return success
    }

    private suspend fun <R> FlowCollector<FlowEvent<R, MessageProgress>>.fail(error: Throwable) {
        logger.error(error) { error.userDescription() }
        emit(FlowEvent.Failure(error.userDescription()))
    }
}

/** Whether this child is a song: not a directory or a video. */
internal val SongDto.isSong: Boolean
    get() = !isDir && !isVideo

internal fun songPath(id: String): String = "subsonic://song/$id"

internal fun SongDto.toSong(): Song {
    val artistNames = artists.names().ifEmpty { listOfNotNull(artist?.takeIf(String::isNotBlank)) }
    val albumArtistNames = albumArtists.names()
    val codec = audioCodec()
    val createdAt = created?.let { date -> runCatching { Instant.parse(date) }.getOrNull() }
    val gain = replayGain?.takeUnless { it.isEmpty() }
    return Song(
        id = 0,
        name = title,
        albumArtist = displayAlbumArtist?.takeIf(String::isNotBlank) ?: albumArtistNames.firstOrNull(),
        artists = artistNames,
        album = album,
        track = track,
        disc = discNumber,
        duration = (duration ?: 0) * 1000,
        date = year?.takeIf { it > 0 }?.let { LocalDate(it, 1, 1) },
        genres = genres.mapNotNull { it.name?.takeIf(String::isNotBlank) }.ifEmpty { listOfNotNull(genre?.takeIf(String::isNotBlank)) },
        path = songPath(id),
        size = size ?: 0,
        mimeType = contentType ?: "Audio/*",
        // The server has no modified date for a song; when it was added keeps the Last Modified sort meaningful
        lastModified = createdAt,
        lastPlayed = null,
        lastCompleted = null,
        playCount = playCount ?: 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = id,
        mediaProvider = MediaProviderType.Subsonic,
        replayGainTrack = gain?.trackGain,
        replayGainAlbum = gain?.albumGain,
        lyrics = null,
        grouping = null,
        bitRate = bitRate?.takeIf { it > 0 },
        bitDepth = losslessBitDepth(codec, bitDepth),
        sampleRate = samplingRate?.takeIf { it > 0 },
        channelCount = channelCount?.takeIf { it > 0 },
        audioCodec = codec,
        // The cover art id carries the art's own hash (`al-<id>_<hash>`), so it changes when the art does
        artworkVersion = coverArt ?: albumId,
        dateAdded = createdAt,
        favouritedAt = starred?.let { date -> runCatching { Instant.parse(date) }.getOrNull() },
        albumArtists = albumArtistNames,
        artistsTag = artistNames,
        artistDisplay = displayArtist?.takeIf(String::isNotBlank) ?: artist,
        compilation = null,
        // OpenSubsonic's song musicBrainzId is the recording's
        mbTrackId = musicBrainzIds(musicBrainzId).firstOrNull(),
        serverAlbumId = albumId,
        serverArtistIds = artists.mapNotNull(ArtistRefDto::id).ifEmpty { listOfNotNull(artistId) },
        serverAlbumArtistIds = albumArtists.mapNotNull(ArtistRefDto::id)
    )
}

private fun List<ArtistRefDto>.names(): List<String> = mapNotNull { it.name?.takeIf(String::isNotBlank) }

/** A server with no ReplayGain tags sends `{}`, or all zeros (Navidrome). */
private fun ReplayGainDto.isEmpty(): Boolean = listOf(trackGain, albumGain, trackPeak, albumPeak).all { it == null || it == 0.0 }

/**
 * The song's codec, from its file suffix: Subsonic reports the container, not the codec. An `.m4a` is AAC or ALAC; a
 * bitrate past any AAC encoder's is taken as ALAC, which the player can't decode, so it's transcoded.
 */
internal fun SongDto.audioCodec(): String? = when (val suffix = suffix?.lowercase()) {
    null, "" -> null
    "m4a", "mp4", "m4b" -> if ((bitRate ?: 0) > MAX_AAC_KBPS) "alac" else "aac"
    else -> suffix
}

private const val MAX_AAC_KBPS = 512
