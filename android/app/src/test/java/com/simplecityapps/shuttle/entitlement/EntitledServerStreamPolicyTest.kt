package com.simplecityapps.shuttle.entitlement

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongDownloadRepository
import com.simplecityapps.shuttle.downloads.SongDownload
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.ProSource
import com.simplecityapps.trial.ServerAccessGate
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EntitledServerStreamPolicyTest {
    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Free(trialUsed = true))
    private val downloads = FakeSongDownloadRepository()
    private val policy = EntitledServerStreamPolicy(downloads, ServerAccessGate(entitlement, startTrial = { false }))
    private val song = createSong(id = 1, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://1")

    private fun download(state: SongDownload.State) {
        downloads.downloads.value = listOf(SongDownload(song.path, state, progress = 1f, bytesDownloaded = 1, contentLength = 1))
    }

    @Test
    fun `a free user can't stream from a server`() = runTest {
        policy.allows(song) shouldBe false
    }

    @Test
    fun `a downloaded song plays for a free user`() = runTest {
        download(SongDownload.State.Completed)

        policy.allows(song) shouldBe true
    }

    @Test
    fun `a download that hasn't finished doesn't count`() = runTest {
        download(SongDownload.State.Downloading)

        policy.allows(song) shouldBe false
    }

    @Test
    fun `trial and Pro users stream`() = runTest {
        entitlement.value = Entitlement.Trial(Instant.DISTANT_FUTURE)
        policy.allows(song) shouldBe true

        entitlement.value = Entitlement.Pro(ProSource.Subscription)
        policy.allows(song) shouldBe true
    }

    @Test
    fun `a denied song is reported as gated`() = runTest(UnconfinedTestDispatcher()) {
        val gated = mutableListOf<com.simplecityapps.shuttle.model.Song>()
        backgroundScope.launch { policy.gatedSongs.collect { gated += it } }

        policy.allows(song)

        gated shouldBe listOf(song)
    }

    @Test
    fun `an allowed song isn't reported as gated`() = runTest(UnconfinedTestDispatcher()) {
        entitlement.value = Entitlement.Pro(ProSource.Subscription)
        val gated = mutableListOf<com.simplecityapps.shuttle.model.Song>()
        backgroundScope.launch { policy.gatedSongs.collect { gated += it } }

        policy.allows(song)

        gated shouldBe emptyList()
    }
}
