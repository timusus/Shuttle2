package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

/**
 * The universal stream URL per platform [StreamProfile] (#603): Android's HLS transcode exactly as it was, iOS's
 * progressive MP3 over the formats its FFmpeg build plays, and a transcode started at an offset for a seek.
 */
class JellyfinStreamProfileTest {
    @Test
    fun `android asks for direct play of what Media3 plays - else an AAC transcode over HLS`() {
        urlFor(StreamProfile.Android, StreamingQuality.Kbps320) shouldBe
            "http://jellyfin.local:8096/Audio/item789/universal?UserId=user456&DeviceId=device-1&PlaySessionId=<session>" +
            "&Container=opus,mp3|mp3,aac|aac,m4a|aac,m4b|aac,flac,webma,webm,wav,ogg&TranscodingContainer=ts" +
            "&TranscodingProtocol=hls&EnableRedirection=true&EnableRemoteMedia=true&AudioCodec=aac" +
            "&MaxStreamingBitrate=320000&ApiKey=token123"
    }

    @Test
    fun `android at original quality sends no cap`() {
        urlFor(StreamProfile.Android, StreamingQuality.Original) shouldBe
            "http://jellyfin.local:8096/Audio/item789/universal?UserId=user456&DeviceId=device-1&PlaySessionId=<session>" +
            "&Container=opus,mp3|mp3,aac|aac,m4a|aac,m4b|aac,flac,webma,webm,wav,ogg&TranscodingContainer=ts" +
            "&TranscodingProtocol=hls&EnableRedirection=true&EnableRemoteMedia=true&AudioCodec=aac" +
            "&ApiKey=token123"
    }

    @Test
    fun `ios asks for direct play of what its FFmpeg build decodes - else a progressive MP3 transcode`() {
        urlFor(StreamProfile.Ios, StreamingQuality.Kbps128) shouldBe
            "http://jellyfin.local:8096/Audio/item789/universal?UserId=user456&DeviceId=device-1&PlaySessionId=<session>" +
            "&Container=mp3|mp3,aac|aac,m4a|aac,m4a|alac,m4b|aac,m4b|alac,mp4|aac,mp4|alac," +
            "flac,ogg,oga,opus,mka,matroska,webm,webma,wav,aiff,aif" +
            "&TranscodingContainer=mp3&TranscodingProtocol=http&EnableRedirection=true&EnableRemoteMedia=true" +
            "&AudioCodec=mp3&MaxStreamingBitrate=128000&ApiKey=token123"
    }

    @Test
    fun `ios never asks for an HLS transcode`() {
        val url = urlFor(StreamProfile.Ios, StreamingQuality.Kbps192)

        url shouldNotContain "hls"
        url shouldNotContain "TranscodingContainer=ts"
    }

    @Test
    fun `a transcode started at an offset carries it in 100 ns ticks`() {
        val url = urlFor(StreamProfile.Ios, StreamingQuality.Kbps128, startPositionMs = 95_500)

        url.substringAfter("&MaxStreamingBitrate=128000") shouldBe "&StartTimeTicks=955000000&ApiKey=token123"
    }

    @Test
    fun `a stream from the start sends no offset`() {
        urlFor(StreamProfile.Ios, StreamingQuality.Kbps128, startPositionMs = 0) shouldNotContain "StartTimeTicks"
    }

    @Test
    fun `every url starts a new play session - so a server never replays an earlier transcode for a seek`() {
        val sessions = List(2) { rawUrlFor(StreamProfile.Ios, StreamingQuality.Kbps128, startPositionMs = 10_000) }
            .map { url -> url.substringAfter("PlaySessionId=").substringBefore('&') }

        sessions.distinct().size shouldBe 2
    }

    private fun urlFor(
        profile: StreamProfile,
        quality: StreamingQuality,
        startPositionMs: Long = 0
    ): String = rawUrlFor(profile, quality, startPositionMs).replace(Regex("PlaySessionId=[^&]+"), "PlaySessionId=<session>")

    private fun rawUrlFor(
        profile: StreamProfile,
        quality: StreamingQuality,
        startPositionMs: Long
    ): String {
        val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "jellyfin").apply {
            address = "http://jellyfin.local:8096"
            authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)
        }
        val authenticationManager = JellyfinAuthenticationManager(
            userService = UserService(createHttpClient(FixtureServer("jellyfin").engine)),
            credentialStore = credentialStore,
            clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice"),
            streamProfile = profile
        )
        val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore())).apply {
            unmeteredQuality.value = quality
        }
        val provider = JellyfinStreamUrlProvider(authenticationManager, StreamingPolicy(streamingSettings, DeliveredFormats()) { false })
        return provider.streamUrl(song(), startPositionMs)
    }

    private fun song() = Song(
        id = 0,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "jellyfin://item/item789",
        size = 0,
        mimeType = "Audio/*",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = null,
        mediaProvider = MediaProviderType.Jellyfin,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
