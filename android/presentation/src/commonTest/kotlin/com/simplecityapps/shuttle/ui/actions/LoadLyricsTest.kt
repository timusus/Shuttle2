package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class LoadLyricsTest {
    private val songRepository = FakeSongRepository()
    private val asked = mutableListOf<Long>()
    private var online: String? = "from online"
    private val onlineSource = object : OnlineLyricsSource {
        override val enabled: Flow<Boolean> = flowOf(true)

        override suspend fun lookup(song: Song): String? {
            asked += song.id
            return online
        }
    }
    private val loadLyrics = LoadLyrics(songRepository, onlineSource)

    @Test
    fun `stored lyrics win and online is not asked`() = runTest {
        songRepository.lyrics[1] = "embedded"

        loadLyrics(createSong(id = 1)) shouldBe "embedded"

        asked shouldBe emptyList()
    }

    @Test
    fun `a song with none stored falls back to online`() = runTest {
        loadLyrics(createSong(id = 2)) shouldBe "from online"

        asked shouldBe listOf(2L)
    }

    @Test
    fun `nowhere to be found is null`() = runTest {
        online = null

        loadLyrics(createSong(id = 3)) shouldBe null
    }
}
