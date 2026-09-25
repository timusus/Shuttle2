package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.shuttle.model.SongFolder
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderDisplayNameTest {
    private val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    @Test
    fun `primary volume shows internal storage`() {
        val folder = Folder(path = listOf(SongFolder.PRIMARY_VOLUME), songCount = 1)

        folder.displayName(resources) shouldBe "Internal storage"
    }

    @Test
    fun `other volume shows other locations`() {
        val folder = Folder(path = listOf(SongFolder.OTHER_VOLUME), songCount = 1)

        folder.displayName(resources) shouldBe "Other locations"
    }

    @Test
    fun `filesystem root shows root`() {
        val folder = Folder(path = listOf(SongFolder.FILESYSTEM_ROOT), songCount = 1)

        folder.displayName(resources) shouldBe "Root"
    }

    @Test
    fun `subfolder shows its own name`() {
        val folder = Folder(path = listOf(SongFolder.PRIMARY_VOLUME, "Music", "Juniper Static"), songCount = 2)

        folder.displayName(resources) shouldBe "Juniper Static"
    }
}
