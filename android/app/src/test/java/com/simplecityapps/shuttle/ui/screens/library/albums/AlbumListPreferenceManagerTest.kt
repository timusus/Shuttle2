package com.simplecityapps.shuttle.ui.screens.library.albums

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumListPreferenceManagerTest {

    private val preferences = AlbumListPreferenceManager(
        GeneralPreferenceManager(
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("album-list-test", Context.MODE_PRIVATE),
        ),
    )

    @Test
    fun `albums start as a grid (#491)`() {
        preferences.albumListViewMode shouldBe ViewMode.Grid
    }

    @Test
    fun `a saved list view mode sticks`() {
        preferences.albumListViewMode = ViewMode.List

        preferences.albumListViewMode shouldBe ViewMode.List
    }
}
