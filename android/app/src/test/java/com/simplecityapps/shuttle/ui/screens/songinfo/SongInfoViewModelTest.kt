package com.simplecityapps.shuttle.ui.screens.songinfo

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongInfoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }

    @Test
    fun `loads the song by id`() = runTest {
        val song = createSong(id = 2, name = "Two")
        songRepository.setSongs(listOf(createSong(id = 1), song))

        val viewModel = SongInfoViewModel(2, songRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe SongInfoUiState(song = song, loading = false)
    }

    @Test
    fun `a song no longer in the library is not found`() = runTest {
        songRepository.setSongs(emptyList())

        val viewModel = SongInfoViewModel(2, songRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value shouldBe SongInfoUiState(song = null, loading = false)
    }

    @Test
    fun `rows format the file details`() {
        val song = createSong().copy(size = 5 * 1024 * 1024, bitRate = 320, bitDepth = 24, sampleRate = 44100, replayGainTrack = -6.5, artists = listOf("A", "B"))

        val rows = song.infoRows().associate { it.label to it.value }

        rows[R.string.song_info_size] shouldBe "5.00 MB"
        rows[R.string.song_info_bit_rate] shouldBe "320 kb/s"
        rows[R.string.song_info_bit_depth] shouldBe "24-bit"
        rows[R.string.song_info_sample_rate] shouldBe "44.1 kHz"
        rows[R.string.song_info_replay_gain_track] shouldBe "-6.50 dB"
        rows[R.string.song_info_replay_gain_album] shouldBe null
        rows[R.string.song_info_artists] shouldBe "A, B"
    }

    @Test
    fun `a whole kHz sample rate has no decimal`() {
        formatSampleRate(48000) shouldBe "48 kHz"
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
