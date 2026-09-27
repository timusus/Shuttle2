package com.simplecityapps.shuttle.ui.screens.settings.about

import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssetBundledTextTest {
    private val bundledText = AssetBundledText(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined)

    @Test
    fun `reads the changelog from the assets`() = runTest {
        bundledText.read("changelog.json")!!.trimStart() shouldStartWith "["
    }

    @Test
    fun `reads the licences metadata from the raw resource the aboutlibraries plugin generates`() = runTest {
        bundledText.read("aboutlibraries.json")!!.trimStart() shouldStartWith "{"
    }

    @Test
    fun `a file the app doesn't bundle reads as null`() = runTest {
        bundledText.read("missing.json").shouldBeNull()
    }
}
