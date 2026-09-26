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

    // Lazy, so a test can hold a grant before the first store (the one that migrates) is created
    private val store by lazy { SafScannerFolderStore(context, settings) }

    private val music = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    private val podcasts = "content://com.android.externalstorage.documents/tree/primary%3APodcasts"

    private fun grant(treeUri: String) = context.contentResolver.takePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    private fun revoke(treeUri: String) = context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    @Test
    fun `a picked folder is kept as an include, with access`() {
        store.add(FolderKind.Include, music) shouldBe true

        store.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(music to true)
    }

    @Test
    fun `a grant revoked outside the app flags its folder as needing access, on the next refresh`() {
        store.add(FolderKind.Include, music)

        revoke(music)
        store.refresh()

        store.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(music to false)
    }

    @Test
    fun `re-adding a flagged folder restores its access without duplicating it`() {
        store.add(FolderKind.Include, music)
        revoke(music)
        store.refresh()

        store.add(FolderKind.Include, music)

        store.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(music to true)
    }

    @Test
    fun `removing a flagged folder drops it, even without a live grant`() {
        store.add(FolderKind.Include, music)
        revoke(music)
        store.refresh()

        store.remove(FolderKind.Include, store.folders.value.includes.single())

        store.folders.value.includes shouldBe emptyList()
    }

    @Test
    fun `a grant already held before the folder was tracked as an include is adopted on load`() {
        grant(music)

        val migrated = SafScannerFolderStore(context, settings)

        migrated.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(music to true)
    }

    @Test
    fun `an extra folder's revoked grant is flagged the same way as an include`() {
        store.add(FolderKind.Extra, music)

        revoke(music)
        store.refresh()

        store.folders.value.extras.map { it.uri to it.hasAccess } shouldBe listOf(music to false)
    }

    @Test
    fun `a removed folder whose grant couldn't be released isn't adopted again`() {
        store.add(FolderKind.Include, music)
        store.remove(FolderKind.Include, store.folders.value.includes.single())
        // As if the release had failed: the grant is still held
        grant(music)

        store.refresh()
        store.folders.value.includes shouldBe emptyList()
        SafScannerFolderStore(context, settings).folders.value.includes shouldBe emptyList()
    }

    @Test
    fun `a user who removed every folder keeps an empty list across launches`() {
        grant(music)
        store.remove(FolderKind.Include, store.folders.value.includes.single())
        grant(podcasts)

        SafScannerFolderStore(context, settings).folders.value.includes shouldBe emptyList()
    }

    @Test
    fun `a grant taken after the migration isn't made a scan root`() {
        store.add(FolderKind.Include, music)
        grant(podcasts)

        store.refresh()

        store.folders.value.includes.map { it.uri } shouldBe listOf(music)
        store.scannerFolders().filter.includes shouldBe listOf(store.folders.value.includes.single().path)
    }

    @Test
    fun `re-granting a flagged folder under another form of its URI restores it without duplicating it`() {
        store.add(FolderKind.Include, music)
        revoke(music)
        store.refresh()

        val sameFolder = "content://com.android.externalstorage.documents/tree/primary:Music"
        store.add(FolderKind.Include, sameFolder)

        store.folders.value.includes.map { it.uri to it.hasAccess } shouldBe listOf(sameFolder to true)
    }
}
