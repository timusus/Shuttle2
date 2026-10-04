package com.simplecityapps.shuttle.shared.downloads

import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.shared.playback.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class OfflineDownloadsTest {
    private class FakeTransport(private val onDevice: Set<String> = emptySet()) : DownloadTransport {
        override var listener: DownloadTransport.Listener? = null
        val started = mutableListOf<Pair<String, DownloadSource>>()
        val removed = mutableListOf<String>()

        override fun restore(): Set<String> = onDevice

        override fun start(
            path: String,
            source: DownloadSource
        ) {
            started += path to source
        }

        override fun remove(path: String) {
            removed += path
        }

        override fun fileUrl(path: String): String = "file:///Downloads/" + DownloadFileNames.fileName(path, "flac")
    }

    private val jellyfin = object : StreamUrlProvider {
        override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

        override fun streamUrl(
            song: Song,
            startPositionMs: Long
        ): String = error("Not streamed")

        override fun downloadSource(song: Song): DownloadSource? = if (song.name == "signed out") null else DownloadSource("https://jellyfin.example/${song.id}/download", song.mimeType)
    }

    private val transport = FakeTransport(onDevice = setOf("jellyfin://item/1"))
    private val downloads = OfflineDownloads(listOf(jellyfin), transport)

    @Test
    fun theFilesOnTheDeviceAreDownloadedAtLaunch() = runTest {
        downloads.downloads.value shouldBe mapOf("jellyfin://item/1" to OfflineDownload(OfflineDownload.State.Completed, 1f))
        downloads.fileUrl("jellyfin://item/1") shouldBe "file:///Downloads/amVsbHlmaW46Ly9pdGVtLzE.flac"
        downloads.observeHeldPaths().first() shouldBe setOf("jellyfin://item/1")
    }

    @Test
    fun aDownloadStartsFromTheProvidersSourceAndCompletes() = runTest {
        downloads.download(remote(2)) shouldBe true
        transport.started shouldBe listOf("jellyfin://item/2" to DownloadSource("https://jellyfin.example/2/download", "audio/flac"))
        downloads.fileUrl("jellyfin://item/2") shouldBe null

        transport.listener!!.onProgress("jellyfin://item/2", 25, 100)
        downloads.downloads.value["jellyfin://item/2"] shouldBe OfflineDownload(OfflineDownload.State.Downloading, 0.25f)
        downloads.observeHeldPaths().first() shouldBe setOf("jellyfin://item/1", "jellyfin://item/2")

        transport.listener!!.onCompleted("jellyfin://item/2")
        downloads.fileUrl("jellyfin://item/2") shouldBe "file:///Downloads/amVsbHlmaW46Ly9pdGVtLzI.flac"
    }

    @Test
    fun aSongAlreadyHeldIsntFetchedAgain() = runTest {
        downloads.download(remote(1)) shouldBe true
        downloads.download(remote(2))
        downloads.download(remote(2))

        transport.started.map { it.first } shouldBe listOf("jellyfin://item/2")
    }

    @Test
    fun aSongWithNoSourceIsntDownloaded() = runTest {
        downloads.download(remote(2).copy(name = "signed out")) shouldBe false
        downloads.download(song(id = 3, path = "/music/3.flac")) shouldBe false

        transport.started shouldBe emptyList()
        downloads.downloads.value.keys shouldBe setOf("jellyfin://item/1")
    }

    @Test
    fun aFailedDownloadIsntHeldAndCanBeRetried() = runTest {
        downloads.download(remote(2))
        transport.listener!!.onFailed("jellyfin://item/2")

        downloads.downloads.value["jellyfin://item/2"]?.state shouldBe OfflineDownload.State.Failed
        downloads.observeHeldPaths().first() shouldBe setOf("jellyfin://item/1")
        downloads.fileUrl("jellyfin://item/2") shouldBe null

        downloads.download(remote(2)) shouldBe true
        transport.started.size shouldBe 2
    }

    @Test
    fun removingDeletesTheFileAndForgetsTheSong() = runTest {
        downloads.remove(remote(1))

        transport.removed shouldBe listOf("jellyfin://item/1")
        downloads.downloads.value shouldBe emptyMap()
        downloads.fileUrl("jellyfin://item/1") shouldBe null
    }

    @Test
    fun aDownloadThatFinishesAfterItsRemovalIsDeletedAgain() = runTest {
        downloads.download(remote(2))
        downloads.remove(remote(2))
        transport.listener!!.onProgress("jellyfin://item/2", 50, 100)
        transport.listener!!.onCompleted("jellyfin://item/2")

        downloads.downloads.value.keys shouldBe setOf("jellyfin://item/1")
        transport.removed shouldBe listOf("jellyfin://item/2", "jellyfin://item/2")
    }

    @Test
    fun aDownloadThatFinishedWhileTheAppWasntRunningIsKept() = runTest {
        transport.listener!!.onCompleted("jellyfin://item/2")

        downloads.downloads.value["jellyfin://item/2"] shouldBe OfflineDownload(OfflineDownload.State.Completed, 1f)
        downloads.fileUrl("jellyfin://item/2") shouldBe "file:///Downloads/amVsbHlmaW46Ly9pdGVtLzI.flac"
        transport.removed shouldBe emptyList()
    }

    @Test
    fun aRemovedDownloadThatsStillReportedRunningStaysRemoved() = runTest {
        downloads.download(remote(2))
        downloads.remove(remote(2))
        transport.listener!!.onRunning("jellyfin://item/2")

        downloads.downloads.value.keys shouldBe setOf("jellyfin://item/1")
    }

    @Test
    fun aSongDownloadedAgainAfterItsRemovalKeepsItsNewFile() = runTest {
        downloads.download(remote(2))
        downloads.remove(remote(2))
        downloads.download(remote(2))
        transport.listener!!.onCompleted("jellyfin://item/2")

        downloads.downloads.value["jellyfin://item/2"]?.state shouldBe OfflineDownload.State.Completed
        transport.removed shouldBe listOf("jellyfin://item/2")
    }

    @Test
    fun aDownloadStillRunningFromAnEarlierLaunchIsPickedUp() = runTest {
        transport.listener!!.onProgress("jellyfin://item/2", 10, 100)
        downloads.downloads.value.keys shouldBe setOf("jellyfin://item/1")

        transport.listener!!.onRunning("jellyfin://item/2")
        transport.listener!!.onProgress("jellyfin://item/2", 50, 100)
        downloads.downloads.value["jellyfin://item/2"] shouldBe OfflineDownload(OfflineDownload.State.Downloading, 0.5f)
    }

    @Test
    fun aLateProgressReportDoesntUndoACompletion() = runTest {
        downloads.download(remote(2))
        transport.listener!!.onCompleted("jellyfin://item/2")
        transport.listener!!.onProgress("jellyfin://item/2", 99, 100)

        downloads.downloads.value["jellyfin://item/2"]?.state shouldBe OfflineDownload.State.Completed
    }

    @Test
    fun theSummaryFollowsTheSongsDownloads() = runTest {
        downloads.summary(listOf(remote(1))) shouldBe DownloadSummary(DownloadSummary.Status.Downloaded, canDownload = false, canRemove = true)
        downloads.summary(listOf(remote(1), remote(2))) shouldBe DownloadSummary(DownloadSummary.Status.NotDownloaded, canDownload = true, canRemove = true)

        downloads.download(remote(2))
        downloads.summary(listOf(remote(1), remote(2))).status shouldBe DownloadSummary.Status.Downloading

        downloads.summary(listOf(song(id = 3))) shouldBe DownloadSummary(DownloadSummary.Status.NotDownloaded, canDownload = false, canRemove = false)
    }

    private fun remote(id: Long) = song(id = id, path = "jellyfin://item/$id").copy(mediaProvider = MediaProviderType.Jellyfin)
}
