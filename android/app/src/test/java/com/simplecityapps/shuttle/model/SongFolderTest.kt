package com.simplecityapps.shuttle.model

import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.SongFolder.inFolderOrder
import io.kotest.matchers.shouldBe
import org.junit.Test

class SongFolderTest {

    // region MediaStore paths

    @Test
    fun `internal storage path resolves relative to the primary volume`() {
        SongFolder.locate("/storage/emulated/0/Music/Juniper Static/Phase Garden/01 Chlorophyll Loop.mp3") shouldBe
            SongFolder.Location(listOf("primary", "Music", "Juniper Static", "Phase Garden"), "01 Chlorophyll Loop.mp3")
    }

    @Test
    fun `secondary user and self primary paths resolve to the primary volume`() {
        SongFolder.locate("/storage/emulated/10/Music/a.mp3").folder shouldBe listOf("primary", "Music")
        SongFolder.locate("/storage/self/primary/Music/a.mp3").folder shouldBe listOf("primary", "Music")
        SongFolder.locate("/sdcard/Music/a.mp3").folder shouldBe listOf("primary", "Music")
    }

    @Test
    fun `sd card path resolves to its volume id`() {
        SongFolder.locate("/storage/1234-ABCD/Music/a.flac") shouldBe
            SongFolder.Location(listOf("1234-ABCD", "Music"), "a.flac")
        SongFolder.locate("/mnt/media_rw/1234-ABCD/Music/a.flac").folder shouldBe listOf("1234-ABCD", "Music")
    }

    @Test
    fun `song at the root of a volume is in the volume folder`() {
        SongFolder.locate("/storage/emulated/0/a.mp3") shouldBe SongFolder.Location(listOf("primary"), "a.mp3")
    }

    @Test
    fun `other absolute paths go under the filesystem root`() {
        SongFolder.locate("/data/music/a.mp3") shouldBe SongFolder.Location(listOf("/", "data", "music"), "a.mp3")
    }

    @Test
    fun `file uri is decoded and treated as a path`() {
        SongFolder.locate("file:///storage/emulated/0/My%20Music/a.mp3") shouldBe
            SongFolder.Location(listOf("primary", "My Music"), "a.mp3")
    }

    // endregion

    // region SAF document URIs

    @Test
    fun `saf tree document uri on primary storage resolves to the same folder as its MediaStore path`() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FJuniper%20Static%2F01%20Chlorophyll%20Loop.mp3"

        SongFolder.locate(uri) shouldBe SongFolder.Location(listOf("primary", "Music", "Juniper Static"), "01 Chlorophyll Loop.mp3")
        SongFolder.locate(uri).folder shouldBe SongFolder.locate("/storage/emulated/0/Music/Juniper Static/01 Chlorophyll Loop.mp3").folder
    }

    @Test
    fun `saf document uri on an sd card resolves to its volume id`() {
        val uri = "content://com.android.externalstorage.documents/tree/1234-ABCD%3A/document/1234-ABCD%3AMusic%2Fa.flac"

        SongFolder.locate(uri) shouldBe SongFolder.Location(listOf("1234-ABCD", "Music"), "a.flac")
    }

    @Test
    fun `saf uri decodes multi byte characters and keeps plus signs`() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FIn%C3%A8s%20Quarrow%2FA+B.mp3"

        SongFolder.locate(uri) shouldBe SongFolder.Location(listOf("primary", "Music", "Inès Quarrow"), "A+B.mp3")
    }

    @Test
    fun `saf home root maps to primary Documents`() {
        val uri = "content://com.android.externalstorage.documents/document/home%3AAudio%2Fa.mp3"

        SongFolder.locate(uri).folder shouldBe listOf("primary", "Documents", "Audio")
    }

    @Test
    fun `downloads provider raw uri resolves as a file path`() {
        val uri = "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fa.mp3"

        SongFolder.locate(uri) shouldBe SongFolder.Location(listOf("primary", "Download"), "a.mp3")
    }

    @Test
    fun `uris without a folder hierarchy fall back to the other folder`() {
        SongFolder.locate("content://media/external/audio/media/42").folder shouldBe listOf(SongFolder.OTHER_VOLUME)
        SongFolder.locate("content://com.android.providers.downloads.documents/document/msf%3A123").folder shouldBe
            listOf(SongFolder.OTHER_VOLUME)
        SongFolder.locate("content://com.example.provider/document/abc").folder shouldBe listOf(SongFolder.OTHER_VOLUME)
    }

    @Test
    fun `malformed escapes are left as they are`() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2F100%25%2Fa%ZZ.mp3"

        SongFolder.locate(uri) shouldBe SongFolder.Location(listOf("primary", "Music", "100%"), "a%ZZ.mp3")
    }

    // endregion

    // region Membership and ordering

    @Test
    fun `isUnder matches the folder and its subfolders only`() {
        val path = "/storage/emulated/0/Music/Juniper Static/a.mp3"

        SongFolder.isUnder(path, listOf("primary", "Music")) shouldBe true
        SongFolder.isUnder(path, listOf("primary", "Music", "Juniper Static")) shouldBe true
        SongFolder.isUnder(path, listOf("primary", "Music", "Radio")) shouldBe false
        SongFolder.isUnder(path, listOf("primary", "Music", "Juniper Static", "Live")) shouldBe false
    }

    @Test
    fun `inFolderOrder lists subfolders before a folder's own songs, in natural order`() {
        val loose = createSong(id = 1, path = "/storage/emulated/0/Music/z.mp3")
        val track10 = createSong(id = 2, path = "/storage/emulated/0/Music/Album/Track 10.mp3")
        val track2 = createSong(id = 3, path = "/storage/emulated/0/Music/Album/Track 2.mp3")
        val other = createSong(id = 4, path = "content://media/external/audio/media/42")
        val sdCard = createSong(id = 5, path = "/storage/1234-ABCD/a.mp3")

        listOf(other, loose, track10, sdCard, track2).inFolderOrder() shouldBe listOf(sdCard, track2, track10, loose, other)
    }

    // endregion
}
