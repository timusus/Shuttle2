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

/** The folders Sources lists follow the folder grants the system holds, including one revoked outside the app. */
@RunWith(RobolectricTestRunner::class)
class SafScannerFolderStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val settings = SourcesSettings(SettingsStore(context.defaultSharedPreferences().apply { edit().clear().commit() }))
    private val store = SafScannerFolderStore(context, settings)

    private val music = "content://com.android.externalstorage.documents/tree/primary%3AMusic"

    @Test
    fun `a picked folder is kept as an include`() {
        store.add(FolderKind.Include, music) shouldBe true

        store.folders.value.includes.map { it.uri } shouldBe listOf(music)
    }

    @Test
    fun `a grant revoked outside the app drops its folder on the next refresh`() {
        store.add(FolderKind.Include, music)

        context.contentResolver.releasePersistableUriPermission(Uri.parse(music), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        store.refresh()

        store.folders.value.includes shouldBe emptyList()
    }
}
