package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SourceReachability
import com.simplecityapps.shuttle.persistence.SourceStateStore
import com.simplecityapps.shuttle.ui.actions.DownloadStatusSource
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import com.simplecityapps.shuttle.ui.actions.OfflineModeSource
import com.simplecityapps.shuttle.ui.actions.SongDownloadStatus
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CopyAvailabilityTest {
    private val offline = MutableStateFlow(false)
    private val downloads = MutableStateFlow(DownloadStatuses())
    private val sourceState = SourceStateStore(InMemoryKeyValueStore())

    private val availability = copyAvailability(
        object : OfflineModeSource {
            override fun observe(): Flow<Boolean> = offline

            override fun set(enabled: Boolean) {
                offline.value = enabled
            }
        },
        object : DownloadStatusSource {
            override fun observe(): Flow<DownloadStatuses> = downloads
        },
        sourceState
    )

    @Test
    fun `online with every server reachable and nothing downloaded ranks on quality alone`() = runTest {
        availability.first() shouldBe SongCopyAvailability()
    }

    @Test
    fun `offline mode completed downloads and a server whose last check failed all reach the ranking`() = runTest {
        offline.value = true
        downloads.value = DownloadStatuses(mapOf("emby://1" to SongDownloadStatus.Downloaded, "emby://2" to SongDownloadStatus.Downloading))
        sourceState.setSourceReachability("Plex", SourceReachability(error = "timeout", checkedAt = Instant.fromEpochSeconds(1)))
        sourceState.setSourceReachability("Jellyfin", SourceReachability(error = null, checkedAt = Instant.fromEpochSeconds(1)))

        availability.first() shouldBe SongCopyAvailability(offline = true, downloadedPaths = setOf("emby://1"), unreachableProviders = setOf(MediaProviderType.Plex))
    }

    @Test
    fun `songs that failed to play rank as missing until their source's marks clear`() = runTest {
        sourceState.setSongMissing("Shuttle", 7, missing = true)
        sourceState.setSongMissing("Jellyfin", 12, missing = true)

        availability.first().missingSongIds shouldBe setOf(7L, 12L)

        sourceState.clearMissingSongs("Shuttle")

        availability.first().missingSongIds shouldBe setOf(12L)
    }
}
