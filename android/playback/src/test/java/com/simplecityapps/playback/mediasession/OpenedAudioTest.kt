package com.simplecityapps.playback.mediasession

import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

/** Matching a file another app opened with us to a library song, and the song that plays it when there's none. */
class OpenedAudioTest {
    private val mediaStoreSong = testSong(id = 1, path = "/storage/emulated/0/Music/Artist/Song.mp3")
    private val treeSong = testSong(
        id = 2,
        path = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FOther%20Song.flac"
    )
    private val library = listOf(mediaStoreSong, treeSong)

    @Test
    fun `matches a library song by its file path`() {
        val opened = OpenedAudio(uri = "content://media/external/audio/media/42", filePath = "/storage/emulated/0/Music/Artist/Song.mp3")

        opened.findIn(library) shouldBe mediaStoreSong
    }

    @Test
    fun `matches a library song whose path is the uri itself`() {
        val opened = OpenedAudio(uri = treeSong.path)

        opened.findIn(library) shouldBe treeSong
    }

    @Test
    fun `matches a folder library song by document id under the same authority`() {
        val opened = OpenedAudio(
            uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FOther%20Song.flac",
            documentId = "primary:Music/Other Song.flac",
            authority = "com.android.externalstorage.documents"
        )

        opened.findIn(library) shouldBe treeSong
    }

    @Test
    fun `doesn't match the same document id under another authority`() {
        val opened = OpenedAudio(
            uri = "content://some.other.provider/document/primary%3AMusic%2FOther%20Song.flac",
            documentId = "primary:Music/Other Song.flac",
            authority = "some.other.provider"
        )

        opened.findIn(library) shouldBe null
    }

    @Test
    fun `finds nothing for a file outside the library`() {
        val opened = OpenedAudio(uri = "content://com.example.fileprovider/attachments/voice.ogg")

        opened.findIn(library) shouldBe null
    }

    @Test
    fun `a transient song plays from the uri with its tags`() {
        val opened = OpenedAudio(
            uri = "content://com.example.fileprovider/attachments/track.mp3",
            displayName = "track.mp3",
            mimeType = "audio/mpeg",
            size = 1234,
            title = "Title",
            artist = "Artist",
            album = "Album",
            durationMs = 61_000
        )

        val song = opened.toTransientSong()

        song.path shouldBe opened.uri
        song.name shouldBe "Title"
        song.artists shouldBe listOf("Artist")
        song.album shouldBe "Album"
        song.duration shouldBe 61_000
        song.size shouldBe 1234
        song.mimeType shouldBe "audio/mpeg"
        song.mediaProvider shouldBe MediaProviderType.MediaStore
        song.mediaProvider.supportsTagEditing shouldBe false
    }

    @Test
    fun `a transient song without tags is named after the file`() {
        OpenedAudio(uri = "content://p/x/1", displayName = "My Recording.m4a", title = " ").toTransientSong().name shouldBe "My Recording"
        OpenedAudio(uri = "file:///sdcard/Download/clip.ogg").toTransientSong().name shouldBe "clip.ogg"
    }

    @Test
    fun `a transient song without a mime type falls back to any audio`() {
        OpenedAudio(uri = "content://p/x/1").toTransientSong().mimeType shouldBe "audio/*"
    }

    @Test
    fun `a transient song's id is negative and stable per uri`() {
        val uris = listOf("content://p/x/1", "content://p/x/2", "file:///a.mp3", "")

        uris.forEach { uri ->
            val id = OpenedAudio(uri = uri).toTransientSong().id
            (id < 0) shouldBe true
            OpenedAudio(uri = uri).toTransientSong().id shouldBe id
        }
        OpenedAudio.transientId("content://p/x/1") shouldNotBe OpenedAudio.transientId("content://p/x/2")
    }

    @Test
    fun `document uri parts are decoded from plain and tree document uris`() {
        OpenedAudio.documentUriParts("content://auth/document/primary%3AMusic%2Fa%20b.mp3") shouldBe ("auth" to "primary:Music/a b.mp3")
        OpenedAudio.documentUriParts("content://auth/tree/primary%3AMusic/document/primary%3AMusic%2F%C3%A9.mp3") shouldBe ("auth" to "primary:Music/é.mp3")
        OpenedAudio.documentUriParts("content://auth/tree/primary%3AMusic") shouldBe null
        OpenedAudio.documentUriParts("/storage/emulated/0/Music/a.mp3") shouldBe null
    }
}
