package com.simplecityapps.shuttle.downloads

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.shuttle.model.Song
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
    private lateinit var preferences: DownloadPreferences
    private lateinit var requirementsManager: DownloadRequirementsManager

    @Before
    fun setUp() {
        val sharedPreferences =
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences("download-requirements-test", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        preferences = DownloadPreferences(sharedPreferences)
        requirementsManager = DownloadRequirementsManager(songDownloadManager, preferences)
    }

    @Test
    fun `wifi only is the default`() {
        preferences.wifiOnly shouldBe true
    }

    @Test
    fun `observing pushes nothing while the preference matches what the manager was built with`() = runTest {
        requirementsManager.observe(backgroundScope)
        runCurrent()

        songDownloadManager.applied shouldBe emptyList()
    }

    @Test
    fun `a stored any-network preference is already in effect at launch, so nothing is pushed`() = runTest {
        preferences.wifiOnly = false

        requirementsManager.observe(backgroundScope)
        runCurrent()

        songDownloadManager.applied shouldBe emptyList()
    }

    @Test
    fun `each change is pushed once, and repeats of the applied value are not`() = runTest {
        requirementsManager.observe(backgroundScope)
        runCurrent()

        preferences.wifiOnly = false
        runCurrent()
        preferences.wifiOnly = false
        runCurrent()
        preferences.wifiOnly = true
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

    override fun remove(song: Song) = Unit

    override fun removeAll() = Unit

    override fun setRequirements(wifiOnly: Boolean) {
        applied += wifiOnly
    }
}
