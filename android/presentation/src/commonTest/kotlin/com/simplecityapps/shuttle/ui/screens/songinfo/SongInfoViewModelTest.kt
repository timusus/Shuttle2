package com.simplecityapps.shuttle.ui.screens.songinfo

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeCopyPreferencesStore
import com.simplecityapps.fakes.FakeOfflineSongs
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.copyAlbumKey
import com.simplecityapps.shuttle.model.copyKey
import com.simplecityapps.shuttle.ui.actions.ClearPrimaryCopy
import com.simplecityapps.shuttle.ui.actions.LoadLyrics
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.NoOnlineLyrics
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.PlayFromCopy
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.SetAlbumPrimarySource
import com.simplecityapps.shuttle.ui.actions.SetPrimaryCopy
import com.simplecityapps.shuttle.ui.text.SongInfoString
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

    private val store = FakeCopyPreferencesStore()
    private val queue = FakeQueueOperations()
    private val playback = FakePlaybackOperations()

    private fun viewModel(songId: Long) = SongInfoViewModel(
        songId,
        ObserveSongs(songRepository, FakeOfflineSongs().filter),
        LoadLyrics(songRepository, NoOnlineLyrics),
        fixedCopyContext(),
        PlayFromCopy(queue, playback, PlaySongs(queue, playback)),
        SetPrimaryCopy(store),
        ClearPrimaryCopy(store),
        SetAlbumPrimarySource(store),
    )

    private val local = createSong(id = 1, path = "/music/1.flac", album = "Album")
    private val emby = createSong(id = 2, path = "emby://2", mediaProvider = MediaProviderType.Emby, album = "Album").copy(externalId = "e2")
    private val plex = createSong(id = 3, path = "plex://3", mediaProvider = MediaProviderType.Plex, album = "Album").copy(externalId = "p3")

    private fun TestScope.loadedMergedViewModel(): SongInfoViewModel {
        songRepository.setSongs(listOf(local.copy(copies = listOf(local, emby, plex))))
        return viewModel(1).also {
            backgroundScope.launch { it.uiState.collect {} }
            advanceUntilIdle()
        }
    }

    @Test
    fun `playing a copy queues that copy`() = runTest {
        loadedMergedViewModel().playCopy(plex)
        advanceUntilIdle()

        queue.lastSetQueue!!.single().id shouldBe 3
    }

    @Test
    fun `a copy that fails to play posts a playback failure`() = runTest {
        val viewModel = loadedMergedViewModel()
        queue.setQueueResult = false

        viewModel.playCopy(plex)
        advanceUntilIdle()

        viewModel.uiState.value.events.map { it.value } shouldBe listOf(MediaActionMessage.PlaybackFailed(null))
    }

    @Test
    fun `a failing primary copy change posts a message instead of throwing`() = runTest {
        val viewModel = loadedMergedViewModel()
        store.updateFailure = IllegalStateException("disk full")

        viewModel.makePrimary(emby)
        advanceUntilIdle()

        viewModel.uiState.value.events.map { it.value } shouldBe listOf(MediaActionMessage.SourceChoiceFailed)
    }

    @Test
    fun `making a copy primary stores its key and a later one replaces it`() = runTest {
        val viewModel = loadedMergedViewModel()

        viewModel.makePrimary(emby)
        viewModel.makePrimary(plex)
        advanceUntilIdle()

        store.preferences.value.primaryCopies shouldBe setOf(plex.copyKey)
    }

    @Test
    fun `automatic choice clears the primary copy`() = runTest {
        val viewModel = loadedMergedViewModel()
        viewModel.makePrimary(emby)

        viewModel.useAutomaticChoice()
        advanceUntilIdle()

        store.preferences.value.primaryCopies shouldBe emptySet()
    }

    @Test
    fun `making a copy primary for the album stores its source for the whole album`() = runTest {
        val viewModel = loadedMergedViewModel()

        viewModel.makePrimaryForAlbum(plex)
        advanceUntilIdle()

        store.preferences.value.primaryAlbums shouldBe setOf(plex.copyAlbumKey)
    }

    @Test
    fun `loads the song by id`() = runTest {
        val song = createSong(id = 2, name = "Two")
        songRepository.setSongs(listOf(createSong(id = 1), song))

        val viewModel = viewModel(2)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe SongInfoUiState(song = song, loading = false)
    }

    @Test
    fun `a merged song shows the copy that plays and lists its copies`() = runTest {
        val local = createSong(id = 1, path = "/music/1.flac")
        val emby = createSong(id = 2, path = "emby://2", mediaProvider = MediaProviderType.Emby)
        songRepository.setSongs(listOf(local.copy(copies = listOf(emby, local))))

        val viewModel = viewModel(1)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.song?.id shouldBe 2L
        viewModel.uiState.value.copies?.rows?.map { it.song.id } shouldBe listOf(2L, 1L)
    }

    @Test
    fun `loads the song's lyrics apart from the song`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 2)))
        songRepository.lyrics[2] = "la la la"

        val viewModel = viewModel(2)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.lyrics shouldBe "la la la"
    }

    @Test
    fun `the lyrics row shows the lyrics it is given`() {
        val rows = createSong().infoSections(lyrics = "la la la").flatMap { it.rows }.associate { it.label to it.value }

        rows[SongInfoString.SONG_INFO_LYRICS] shouldBe "la la la"
    }

    @Test
    fun `a song no longer in the library is not found`() = runTest {
        songRepository.setSongs(emptyList())

        val viewModel = viewModel(2)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe SongInfoUiState(song = null, loading = false)
    }

    @Test
    fun `rows format the file details`() {
        val song = createSong().copy(size = 5 * 1024 * 1024, bitRate = 320, bitDepth = 24, sampleRate = 44100, replayGainTrack = -6.5, artists = listOf("A", "B"))

        val rows = song.infoSections(lyrics = null).flatMap { it.rows }.associate { it.label to it.value }

        rows[SongInfoString.SONG_INFO_SIZE] shouldBe "5.00 MB"
        rows[SongInfoString.SONG_INFO_BIT_RATE] shouldBe "320 kb/s"
        rows[SongInfoString.SONG_INFO_BIT_DEPTH] shouldBe "24-bit"
        rows[SongInfoString.SONG_INFO_SAMPLE_RATE] shouldBe "44.1 kHz"
        rows[SongInfoString.SONG_INFO_REPLAY_GAIN_TRACK] shouldBe "-6.50 dB"
        rows[SongInfoString.SONG_INFO_REPLAY_GAIN_ALBUM] shouldBe null
        rows[SongInfoString.SONG_INFO_ARTISTS] shouldBe "A, B"
    }

    @Test
    fun `source row names the provider`() {
        fun source(type: MediaProviderType) = createSong().copy(mediaProvider = type).infoSections(lyrics = null).flatMap { it.rows }.first { it.label == SongInfoString.SONG_INFO_SOURCE }

        source(MediaProviderType.Shuttle) shouldBe SongInfoRow(SongInfoString.SONG_INFO_SOURCE, value = null, valueKey = SongInfoString.SONG_INFO_SOURCE_THIS_DEVICE)
        source(MediaProviderType.MediaStore) shouldBe SongInfoRow(SongInfoString.SONG_INFO_SOURCE, value = null, valueKey = SongInfoString.SONG_INFO_SOURCE_THIS_DEVICE)
        source(MediaProviderType.Jellyfin) shouldBe SongInfoRow(SongInfoString.SONG_INFO_SOURCE, value = "Jellyfin")
        source(MediaProviderType.Emby) shouldBe SongInfoRow(SongInfoString.SONG_INFO_SOURCE, value = "Emby")
        source(MediaProviderType.Plex) shouldBe SongInfoRow(SongInfoString.SONG_INFO_SOURCE, value = "Plex")
        source(MediaProviderType.Subsonic) shouldBe SongInfoRow(SongInfoString.SONG_INFO_SOURCE, value = "Subsonic")
    }

    @Test
    fun `rows are grouped into tags - file and playback cards`() {
        val sections = createSong().infoSections(lyrics = null).associate { it.title to it.rows.map(SongInfoRow::label) }

        sections.keys.toList() shouldBe listOf(SongInfoString.SONG_INFO_SECTION_TAGS, SongInfoString.SONG_INFO_SECTION_FILE, SongInfoString.SONG_INFO_SECTION_PLAYBACK)
        sections.getValue(SongInfoString.SONG_INFO_SECTION_TAGS) shouldContain SongInfoString.SONG_INFO_ALBUM
        val fileRows = sections.getValue(SongInfoString.SONG_INFO_SECTION_FILE)
        fileRows.indexOf(SongInfoString.SONG_INFO_SOURCE) shouldBe fileRows.indexOf(SongInfoString.SONG_INFO_PATH) - 1
        sections.getValue(SongInfoString.SONG_INFO_SECTION_PLAYBACK) shouldContain SongInfoString.SONG_INFO_PLAY_COUNT
    }

    @Test
    fun `chips show the format - bit rate and sample rate the song has`() {
        createSong().copy(mimeType = "audio/flac", bitRate = 1024, sampleRate = 96000).infoChips() shouldBe listOf("FLAC", "1024 kb/s", "96 kHz")
        createSong().copy(mimeType = "audio/mpeg", bitRate = null, sampleRate = 44100).infoChips() shouldBe listOf("MP3", "44.1 kHz")
        createSong().copy(mimeType = "audio/x-wav", audioCodec = "pcm_s24le", bitRate = null, sampleRate = 96000).infoChips() shouldBe listOf("PCM", "96 kHz")
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
        val rows = createSong().copy(track = 0, disc = 0, channelCount = 0).infoSections(lyrics = null).flatMap { it.rows }.associate { it.label to it.value }

        rows[SongInfoString.SONG_INFO_TRACK_NUMBER] shouldBe null
        rows[SongInfoString.SONG_INFO_DISC] shouldBe null
        rows[SongInfoString.SONG_INFO_CHANNEL_COUNT] shouldBe null
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
