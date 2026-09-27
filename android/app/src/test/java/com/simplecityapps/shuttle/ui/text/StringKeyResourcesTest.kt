package com.simplecityapps.shuttle.ui.text

import android.content.res.Resources
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.text
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Every shared string key names an existing Android resource of that name, so iOS can key its catalogue the same way. */
@RunWith(RobolectricTestRunner::class)
class StringKeyResourcesTest {
    private val resources: Resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun `every StringKey maps to the string resource of its name`() {
        StringKey.entries.forEach { key ->
            resources.getResourceTypeName(key.resId) shouldBe "string"
            resources.getResourceEntryName(key.resId) shouldBe key.key
            resources.getString(key.resId).shouldNotBeBlank()
        }
    }

    @Test
    fun `every PluralKey maps to the plurals resource of its name`() {
        PluralKey.entries.forEach { key ->
            resources.getResourceTypeName(key.resId) shouldBe "plurals"
            resources.getResourceEntryName(key.resId) shouldBe key.key
            resources.getQuantityString(key.resId, 2, 2, "x").shouldNotBeBlank()
        }
    }

    @Test
    fun `arguments, nested text and plurals resolve`() {
        resources.getString(MediaActionMessage.AddedToFavourites(songCount = 1).text()) shouldBe "1 song added to Favorites"
        resources.getString(MediaActionMessage.AddedToPlaylist("Road trip", songCount = 3).text()) shouldBe "3 songs added to Road trip"
        resources.getString(MediaActionMessage.PlaybackFailed(reason = null).text()) shouldBe "Couldn't play: An unknown error occurred"
        resources.getString(MediaActionMessage.DownloadRemoved(songCount = 1).text()) shouldBe "Download removed"
    }
}
