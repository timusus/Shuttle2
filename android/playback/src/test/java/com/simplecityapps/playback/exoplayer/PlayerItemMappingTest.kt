package com.simplecityapps.playback.exoplayer

import android.net.Uri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins how a song reaches ExoPlayer: [MediaInfoMediaResolver] turns the provider's media info into
 * a [ResolvedMedia], and a [PlayerItem] maps to a Media3 MediaItem and back unchanged, which
 * [ExoPlayerPlayback.loadNext] relies on to recognise an already queued next item.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerItemMappingTest {
    private val replayGain = ReplayGain(trackGain = -6.5, albumGain = -4.0)

    @Test
    fun `a local file path keeps its uri, mime type and ReplayGain`() {
        val item = PlayerItem(uri = "/storage/emulated/0/Music/A Song #1 (Live).flac", mimeType = "audio/flac", replayGain = replayGain)

        val mediaItem = item.toMediaItem()

        mediaItem.localConfiguration!!.uri.toString() shouldBe item.uri
        mediaItem.localConfiguration!!.mimeType shouldBe "audio/flac"
        mediaItem.localConfiguration!!.tag shouldBe replayGain
        mediaItem.toPlayerItem() shouldBe item
    }

    @Test
    fun `a content uri round-trips unchanged`() {
        val item = PlayerItem(uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Fa.mp3", mimeType = "audio/mpeg", replayGain = null)

        item.toMediaItem().toPlayerItem() shouldBe item
    }

    @Test
    fun `a stream url keeps its query`() {
        val item = PlayerItem(uri = "https://server:8096/Audio/42/universal?UserId=u&api_key=k&Container=flac%2Cmp3", mimeType = "audio/*", replayGain = null)

        item.toMediaItem().toPlayerItem() shouldBe item
    }

    /** Why [ExoPlayerPlayback] normalises the mime type before queueing an item. */
    @Test
    fun `Media3 normalises the mime type, so an unnormalised one does not round-trip`() {
        PlayerItem(uri = "/music/a.flac", mimeType = "Audio/X-FLAC", replayGain = null).toMediaItem().toPlayerItem().mimeType shouldBe "audio/flac"
    }

    @Test
    fun `an item without ReplayGain maps back without one`() {
        val item = PlayerItem(uri = "/music/a.mp3", mimeType = null, replayGain = null)

        val mediaItem = item.toMediaItem()

        mediaItem.localConfiguration!!.tag shouldBe null
        mediaItem.toPlayerItem() shouldBe item
    }

    @Test
    fun `the resolver passes the provider's uri, mime type and remoteness through`() = runTest {
        val song = testSong(1)
        val resolver =
            MediaInfoMediaResolver(
                FixedMediaInfoProvider(MediaInfo(path = Uri.parse("https://server/Audio/1/stream?static=true"), mimeType = "audio/flac", isRemote = true))
            )

        resolver.resolve(song) shouldBe ResolvedMedia(uri = "https://server/Audio/1/stream?static=true", mimeType = "audio/flac", isRemote = true)
    }

    private class FixedMediaInfoProvider(private val mediaInfo: MediaInfo) : MediaInfoProvider {
        override fun handles(uri: Uri): Boolean = true

        override suspend fun getMediaInfo(
            song: Song,
            castCompatibilityMode: Boolean
        ): MediaInfo = mediaInfo

        override suspend fun downloadUri(song: Song): Uri? = mediaInfo.path

        override suspend fun downloadFallbackUri(
            path: String,
            responseCode: Int
        ): Uri? = null
    }
}
