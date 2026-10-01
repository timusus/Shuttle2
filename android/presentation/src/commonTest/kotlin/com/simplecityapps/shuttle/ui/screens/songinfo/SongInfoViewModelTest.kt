package com.simplecityapps.shuttle.ui.screens.songinfo

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.text.StringKey
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SongInfoViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }

    @Test
    fun `loads the song by id`() = runTest {
        val song = createSong(id = 2, name = "Two")
        songRepository.setSongs(listOf(createSong(id = 1), song))

        val viewModel = SongInfoViewModel(2, ObserveSongs(songRepository))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe SongInfoUiState(song = song, loading = false)
    }

    @Test
    fun `a song no longer in the library is not found`() = runTest {
        songRepository.setSongs(emptyList())

        val viewModel = SongInfoViewModel(2, ObserveSongs(songRepository))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe SongInfoUiState(song = null, loading = false)
    }

    @Test
    fun `rows format the file details`() {
        val song = createSong().copy(size = 5 * 1024 * 1024, bitRate = 320, bitDepth = 24, sampleRate = 44100, replayGainTrack = -6.5, artists = listOf("A", "B"))

        val rows = song.infoSections().flatMap { it.rows }.associate { it.label to it.value }

        rows[StringKey.SONG_INFO_SIZE] shouldBe "5.00 MB"
        rows[StringKey.SONG_INFO_BIT_RATE] shouldBe "320 kb/s"
        rows[StringKey.SONG_INFO_BIT_DEPTH] shouldBe "24-bit"
        rows[StringKey.SONG_INFO_SAMPLE_RATE] shouldBe "44.1 kHz"
        rows[StringKey.SONG_INFO_REPLAY_GAIN_TRACK] shouldBe "-6.50 dB"
        rows[StringKey.SONG_INFO_REPLAY_GAIN_ALBUM] shouldBe null
        rows[StringKey.SONG_INFO_ARTISTS] shouldBe "A, B"
    }

    @Test
    fun `rows are grouped into tags - file and playback cards`() {
        val sections = createSong().infoSections().associate { it.title to it.rows.map(SongInfoRow::label) }

        sections.keys.toList() shouldBe listOf(StringKey.SONG_INFO_SECTION_TAGS, StringKey.SONG_INFO_SECTION_FILE, StringKey.SONG_INFO_SECTION_PLAYBACK)
        sections.getValue(StringKey.SONG_INFO_SECTION_TAGS) shouldContain StringKey.SONG_INFO_ALBUM
        sections.getValue(StringKey.SONG_INFO_SECTION_FILE) shouldContain StringKey.SONG_INFO_PATH
        sections.getValue(StringKey.SONG_INFO_SECTION_PLAYBACK) shouldContain StringKey.SONG_INFO_PLAY_COUNT
    }

    @Test
    fun `chips show the format - bit rate and sample rate the song has`() {
        createSong().copy(mimeType = "audio/flac", bitRate = 1024, sampleRate = 96000).infoChips() shouldBe listOf("FLAC", "1024 kb/s", "96 kHz")
        createSong().copy(mimeType = "audio/mpeg", bitRate = null, sampleRate = 44100).infoChips() shouldBe listOf("MP3", "44.1 kHz")
    }

    @Test
    fun `a MIME type reads as its format`() {
        formatName("audio/x-flac") shouldBe "FLAC"
        formatName("audio/mp4") shouldBe "M4A"
        formatName("audio/ogg; codecs=opus") shouldBe "OGG"
        formatName("") shouldBe null
    }

    @Test
    fun `a whole kHz sample rate has no decimal`() {
        formatSampleRate(48000) shouldBe "48 kHz"
    }

    @Test
    fun `a fractional kHz sample rate keeps its second decimal`() {
        formatSampleRate(44100) shouldBe "44.1 kHz"
        formatSampleRate(22050) shouldBe "22.05 kHz"
    }

    @Test
    fun `zero track - disc and channel count are missing`() {
        val rows = createSong().copy(track = 0, disc = 0, channelCount = 0).infoSections().flatMap { it.rows }.associate { it.label to it.value }

        rows[StringKey.SONG_INFO_TRACK_NUMBER] shouldBe null
        rows[StringKey.SONG_INFO_DISC] shouldBe null
        rows[StringKey.SONG_INFO_CHANNEL_COUNT] shouldBe null
    }

    @Test
    fun `a document path shows the path inside its volume`() {
        val song = createSong(path = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FAlbum%2F01%20Song.flac")

        song.displayPath shouldBe "Music/Album/01 Song.flac"
    }

    @Test
    fun `a file path shows as it is`() {
        createSong(path = "/storage/emulated/0/Music/song.mp3").displayPath shouldBe "/storage/emulated/0/Music/song.mp3"
    }
}
