package com.simplecityapps.shuttle.ui.screens.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The folders Sources lists follow the folder grants the system holds, but one revoked outside the app stays
 * listed and flagged (#479) rather than disappearing.
 */
@RunWith(RobolectricTestRunner::class)
class SafScannerFolderStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val settings = SourcesSettings(SettingsStore(context.defaultSharedPreferences().apply { edit().clear().commit() }))
    private val store = SafScannerFolderStore(context, settings)

    private val music = "content://com.android.externalstorage.documents/tree/primary%3AMusic"

    @Test
    fun `a picked folder is kept as an include, with access`() {
        store.add(FolderKind.Include, music) shouldBe true

        store.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(music to true)
    }

    @Test
    fun `a grant revoked outside the app flags its folder as needing access, on the next refresh`() {
        store.add(FolderKind.Include, music)

        context.contentResolver.releasePersistableUriPermission(Uri.parse(music), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        store.refresh()

        store.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(music to false)
    }

    @Test
    fun `re-adding a flagged folder restores its access without duplicating it`() {
        store.add(FolderKind.Include, music)
        context.contentResolver.releasePersistableUriPermission(Uri.parse(music), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        store.refresh()

        store.add(FolderKind.Include, music)

        store.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(music to true)
    }

    @Test
    fun `removing a flagged folder drops it, even without a live grant`() {
        store.add(FolderKind.Include, music)
        context.contentResolver.releasePersistableUriPermission(Uri.parse(music), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        store.refresh()

        store.remove(FolderKind.Include, store.folders.value.includes.single())

        store.folders.value.includes shouldBe emptyList()
    }

    @Test
    fun `a grant already held before the folder was tracked as an include is adopted on load`() {
        context.contentResolver.takePersistableUriPermission(Uri.parse(music), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val migrated = SafScannerFolderStore(context, settings)

        migrated.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(music to true)
    }

    @Test
    fun `an extra folder's revoked grant is flagged the same way as an include`() {
        store.add(FolderKind.Extra, music)

        context.contentResolver.releasePersistableUriPermission(Uri.parse(music), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        store.refresh()

        store.folders.value.extras.map { it.uri to it.hasAccess } shouldBe listOf(music to false)
    }
}
