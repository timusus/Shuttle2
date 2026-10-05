package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.createSong
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.mediaprovider.TagReadFile
import com.simplecityapps.mediaprovider.TagReadGuard
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** #874: a file TagLib crashed on is quarantined, so the editor leaves it unread rather than crashing on it again. */
@RunWith(RobolectricTestRunner::class)
class DeviceTagFileAccessTest {
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val guard = TagReadGuard(Files.createTempDirectory("tag-reads").toFile(), preferences, { null })

    // The native library isn't loaded on the JVM: a quarantined file must never reach it
    private val access = DeviceTagFileAccess(ApplicationProvider.getApplicationContext(), mockk<KTagLib>(), FileScanner(), guard)

    private val song = createSong()

    init {
        preferences.quarantineTagRead(TagReadFile(song.path, song.size, song.lastModified!!.toEpochMilliseconds()).key)
    }

    @Test
    fun `a quarantined song reads as unreadable`() = runBlocking<Unit> {
        access.read(song).shouldBeNull()
    }

    @Test
    fun `a quarantined song is not written`() = runBlocking<Unit> {
        access.write(song, mapOf("TITLE" to listOf("New"))) shouldBe false
    }
}
