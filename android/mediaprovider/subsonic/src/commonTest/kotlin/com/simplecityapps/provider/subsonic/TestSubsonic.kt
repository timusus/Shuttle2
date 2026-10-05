package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.ServerStrings
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.subsonic.http.SubsonicService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.runBlocking

/** [ServerStrings] for tests, which assert on these rather than on a platform's string resources. */
object TestServerStrings : ServerStrings {
    override val addressMissing = "No server address"

    override val authenticationError = "Signing in failed"

    override val unknownName = "Unknown"
}

/** A Subsonic client against a [FixtureServer] serving JSON fixtures captured from Navidrome (credentials scrubbed). */
class TestSubsonic : AutoCloseable {
    val server = FixtureServer("subsonic")

    val service = SubsonicService(createHttpClient(server.engine), clientName = "Shuttle")

    private val preferences = SecurePreferenceManager(InMemoryKeyValueStore())

    val credentialStore = ServerCredentialStore(preferences, "subsonic")

    val authenticationManager = SubsonicAuthenticationManager(service, credentialStore, preferences)

    /** Signs in to a server that answers `ping` with [ping] (and lists [extensions]), then forgets the requests it took. */
    fun signIn(
        ping: String = "ping.json",
        extensions: String = "extensions.json",
        login: LoginCredentials = LoginCredentials(USERNAME, PASSWORD)
    ) {
        server.respond(PING, ping)
        server.respond(EXTENSIONS, extensions)
        authenticationManager.setAddress(server.address)
        runBlocking { authenticationManager.authenticate(server.address, login).getOrThrow() }
        server.clearRequests()
    }

    override fun close() = server.close()

    companion object {
        const val USERNAME = "shuttle"
        const val PASSWORD = "sesame"

        const val PING = "/rest/ping.view"
        const val EXTENSIONS = "/rest/getOpenSubsonicExtensions.view"
        const val SEARCH = "/rest/search3.view"
        const val ALBUM_LIST = "/rest/getAlbumList2.view"
        const val ALBUM = "/rest/getAlbum.view"
        const val ARTIST = "/rest/getArtist.view"
        const val PLAYLISTS = "/rest/getPlaylists.view"
        const val PLAYLIST = "/rest/getPlaylist.view"
        const val STAR = "/rest/star.view"
        const val UNSTAR = "/rest/unstar.view"
        const val SCROBBLE = "/rest/scrobble.view"
        const val TRANSCODE_DECISION = "/rest/getTranscodeDecision.view"
    }
}

/** The request's query parameter [name]. */
fun HttpRequestData.parameter(name: String): String? = url.parameters[name]

/** A Subsonic song, as the sync makes them. */
fun subsonicSong(
    externalId: String? = "5zTXFMk8oDiQF9kh1gqcJE",
    mimeType: String = "audio/flac",
    bitRate: Int? = 778,
    audioCodec: String? = "flac",
    duration: Int = 142_000
) = Song(
    id = 0,
    name = "One Jump Ahead",
    albumArtist = "Alan Menken",
    artists = listOf("Alan Menken"),
    album = "Aladdin",
    track = 3,
    disc = 1,
    duration = duration,
    date = null,
    genres = emptyList(),
    path = "subsonic://song/$externalId",
    size = 0,
    mimeType = mimeType,
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    externalId = externalId,
    mediaProvider = MediaProviderType.Subsonic,
    lyrics = null,
    grouping = null,
    bitRate = bitRate,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
    audioCodec = audioCodec,
    serverAlbumId = "4nxF0cOO4HAm80KTNDfkof",
    serverArtistIds = listOf("07LA8XP6U5De7mzuoBVPz4")
)
