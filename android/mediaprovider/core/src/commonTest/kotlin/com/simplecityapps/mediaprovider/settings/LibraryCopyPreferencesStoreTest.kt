package com.simplecityapps.mediaprovider.settings

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class LibraryCopyPreferencesStoreTest {
    private val settings = LibrarySettings(SettingsStore(InMemoryKeyValueStore()))
    private val store = LibraryCopyPreferencesStore(settings)

    @Test
    fun `a song change and an album change made together are both kept`() = runTest {
        val count = 500
        withContext(Dispatchers.Default) {
            repeat(count) { i ->
                launch { store.update { it.copy(primaryCopies = it.primaryCopies + "song:$i") } }
                launch { store.update { it.copy(primaryAlbums = it.primaryAlbums + "album:$i") } }
            }
        }

        settings.primaryCopies.value shouldBe (0 until count).map { "song:$it" }.toSet()
        settings.primaryAlbums.value shouldBe (0 until count).map { "album:$it" }.toSet()
    }
}
