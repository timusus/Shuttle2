package com.simplecityapps.playback.chromecast

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.playback.queue.toMediaItem
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CastMediaItemConverterTest {
    private val streams = CastStreams(FakeMediaInfoProvider(), EmptyCoroutineContext)

    private val converter = CastMediaItemConverter(hostAddress = { "192.168.1.20" }, streams = streams, unknown = "Unknown")

    private val song = testSong(7, name = "Title", mimeType = "audio/flac", duration = 215_000)
        .copy(artists = listOf("Artist"), album = "Album")

    @Test
    fun `a Wi-Fi address reads lowest byte first`() {
        CastMediaItemConverter.formatIpAddress(0x1401A8C0) shouldBe "192.168.1.20"
    }

    @Test
    fun `an entry streams from the phone's server, with its metadata`() {
        val media = converter.toMediaQueueItem(QueueEntry(uid = 42, song = song).toMediaItem()).media!!

        media.contentUrl shouldBe "http://192.168.1.20:5000/${streams.key}/songs/7/audio"
        media.streamType shouldBe MediaInfo.STREAM_TYPE_BUFFERED
        media.contentType shouldBe "audio/flac"
        media.streamDuration shouldBe 215_000L
        val metadata = media.metadata!!
        metadata.mediaType shouldBe MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
        metadata.getString(MediaMetadata.KEY_ARTIST) shouldBe "Artist"
        metadata.getString(MediaMetadata.KEY_ALBUM_TITLE) shouldBe "Album"
        metadata.getString(MediaMetadata.KEY_TITLE) shouldBe "Title"
        metadata.images.single().url.toString() shouldBe "http://192.168.1.20:5000/${streams.key}/songs/7/artwork"
    }

    @Test
    fun `a new session's items carry its new key`() {
        val before = converter.toMediaQueueItem(QueueEntry(uid = 1, song = song).toMediaItem()).media!!.contentUrl!!
        streams.newSession()

        val after = converter.toMediaQueueItem(QueueEntry(uid = 1, song = song).toMediaItem()).media!!

        after.contentUrl shouldNotBe before
        after.contentUrl shouldBe "http://192.168.1.20:5000/${streams.key}/songs/7/audio"
        after.metadata!!.images.single().url.toString() shouldBe "http://192.168.1.20:5000/${streams.key}/songs/7/artwork"
    }

    @Test
    fun `a remote song goes out as the type its stream was resolved as`() = runTest {
        val remote = remoteSong(8)
        streams.resolve(listOf(remote))

        val media = converter.toMediaQueueItem(QueueEntry(uid = 3, song = remote).toMediaItem()).media!!

        media.contentType shouldBe FakeMediaInfoProvider.TRANSCODED
        media.contentUrl shouldBe "http://192.168.1.20:5000/${streams.key}/songs/8/audio"
    }

    @Test
    fun `missing tags read as unknown`() {
        val untagged = song.copy(name = null, artists = emptyList(), album = null)

        val metadata = converter.toMediaQueueItem(QueueEntry(uid = 1, song = untagged).toMediaItem()).media!!.metadata!!

        metadata.getString(MediaMetadata.KEY_ARTIST) shouldBe "Unknown"
        metadata.getString(MediaMetadata.KEY_ALBUM_TITLE) shouldBe "Unknown"
        metadata.getString(MediaMetadata.KEY_TITLE) shouldBe "Unknown"
    }

    @Test
    fun `two entries for the same song are told apart`() {
        val first = converter.toMediaQueueItem(QueueEntry(uid = 1, song = song).toMediaItem())
        val second = converter.toMediaQueueItem(QueueEntry(uid = 2, song = song).toMediaItem())

        (first.media!!.contentId == second.media!!.contentId) shouldBe false
    }

    @Test
    fun `an item the receiver reports back is the entry's own`() {
        val item = QueueEntry(uid = 42, song = song).toMediaItem()

        converter.toMediaItem(converter.toMediaQueueItem(item)) shouldBeSameInstanceAs item
    }

    @Test
    fun `an item no longer sent comes back without an entry, playing its stream`() {
        val item = QueueEntry(uid = 42, song = song).toMediaItem()
        val queueItem = converter.toMediaQueueItem(item)
        converter.retainOnly(emptyList())

        val reported = converter.toMediaItem(queueItem)

        reported.queueEntryOrNull.shouldBeNull()
        reported.localConfiguration!!.uri.toString() shouldBe "http://192.168.1.20:5000/${streams.key}/songs/7/audio"
    }

    @Test
    fun `an item another sender queued comes back without an entry`() {
        val foreign = MediaQueueItem.Builder(MediaInfo.Builder("http://elsewhere/track.mp3").build()).build()

        converter.toMediaItem(foreign).queueEntryOrNull.shouldBeNull()
    }
}
