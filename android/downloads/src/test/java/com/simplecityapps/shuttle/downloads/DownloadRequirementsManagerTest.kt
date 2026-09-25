package com.simplecityapps.shuttle.downloads

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DownloadRequirementsManagerTest {
    private val songDownloadManager = FakeSongDownloadManager()
    private lateinit var settings: DownloadSettings
    private lateinit var requirementsManager: DownloadRequirementsManager

    @Before
    fun setUp() {
        val sharedPreferences =
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences("download-requirements-test", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        settings = DownloadSettings(SettingsStore(sharedPreferences))
        requirementsManager = DownloadRequirementsManager(songDownloadManager, settings)
    }

    @Test
    fun `wifi only is the default`() {
        settings.wifiOnly.value shouldBe true
    }

    @Test
    fun `observing pushes nothing while the preference matches what the manager was built with`() = runTest {
        requirementsManager.observe(backgroundScope)
        runCurrent()

        songDownloadManager.applied shouldBe emptyList()
    }

    @Test
    fun `a stored any-network preference is already in effect at launch, so nothing is pushed`() = runTest {
        settings.wifiOnly.value = false

        requirementsManager.observe(backgroundScope)
        runCurrent()

        songDownloadManager.applied shouldBe emptyList()
    }

    @Test
    fun `each change is pushed once, and repeats of the applied value are not`() = runTest {
        requirementsManager.observe(backgroundScope)
        runCurrent()

        settings.wifiOnly.value = false
        runCurrent()
        settings.wifiOnly.value = false
        runCurrent()
        settings.wifiOnly.value = true
        runCurrent()

        songDownloadManager.applied shouldBe listOf(false, true)
    }
}

private class FakeSongDownloadManager : SongDownloadManager {
    val applied = mutableListOf<Boolean>()

    override fun download(
        song: Song,
        uri: Uri
    ) = Unit

    override fun download(
        path: String,
        mimeType: String,
        uri: Uri
    ) = Unit

    override fun remove(song: Song) = Unit

    override fun removeAll() = Unit

    override fun setRequirements(wifiOnly: Boolean) {
        applied += wifiOnly
    }
}
