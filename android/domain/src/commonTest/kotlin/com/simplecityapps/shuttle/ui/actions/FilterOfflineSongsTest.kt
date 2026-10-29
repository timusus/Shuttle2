package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class FilterOfflineSongsTest {
    private val mode = MutableStateFlow(false)
    private val statuses = MutableStateFlow(DownloadStatuses())

    private val filter = FilterOfflineSongs(
        object : OfflineModeSource {
            override fun observe(): Flow<Boolean> = mode

            override fun set(enabled: Boolean) {
                mode.value = enabled
            }
        },
        object : DownloadStatusSource {
            override fun observe(): Flow<DownloadStatuses> = statuses
        },
    )

    private val local = song(id = 1, name = "Local", path = "/music/1.mp3")
    private val saved = song(id = 2, name = "Saved", path = "remote://2", mediaProvider = MediaProviderType.Jellyfin)
    private val partial = song(id = 3, name = "Partial", path = "remote://3", mediaProvider = MediaProviderType.Plex)
    private val streamed = song(id = 4, name = "Streamed", path = "remote://4", mediaProvider = MediaProviderType.Subsonic)
    private val library = listOf(local, saved, partial, streamed)

    private suspend fun visible() = filter(flowOf(library)).first().map { it.name }

    @Test
    fun `with Offline mode off every song is listed`() = runTest {
        statuses.value = DownloadStatuses(mapOf("remote://2" to SongDownloadStatus.Downloaded))

        visible() shouldBe listOf("Local", "Saved", "Partial", "Streamed")
    }

    @Test
    fun `with Offline mode on local and fully downloaded songs remain`() = runTest {
        mode.value = true
        statuses.value = DownloadStatuses(mapOf("remote://2" to SongDownloadStatus.Downloaded, "remote://3" to SongDownloadStatus.Downloading))

        visible() shouldBe listOf("Local", "Saved")
    }

    @Test
    fun `a download finishing brings its song back while offline`() = runTest {
        mode.value = true
        visible() shouldBe listOf("Local")

        statuses.value = DownloadStatuses(mapOf("remote://4" to SongDownloadStatus.Downloaded))

        visible() shouldBe listOf("Local", "Streamed")
    }

    @Test
    fun `switching Offline mode off restores the whole library`() = runTest {
        mode.value = true
        visible() shouldBe listOf("Local")

        mode.value = false

        visible() shouldBe listOf("Local", "Saved", "Partial", "Streamed")
    }

    @Test
    fun `offline a merged song stays listed and playable while any of its copies is downloaded`() = runTest {
        mode.value = true
        statuses.value = DownloadStatuses(mapOf("remote://2" to SongDownloadStatus.Downloaded))
        val merged = streamed.copy(copies = listOf(streamed, saved))
        val modeSource = object : OfflineModeSource {
            override fun observe(): Flow<Boolean> = mode

            override fun set(enabled: Boolean) {}
        }
        val playable = FilterPlayableSongs(
            modeSource,
            object : DownloadStatusSource {
                override fun observe(): Flow<DownloadStatuses> = statuses
            }
        )

        filter(flowOf(listOf(merged))).first() shouldBe listOf(merged)
        playable(listOf(merged)) shouldBe listOf(merged)
    }
}
