package com.simplecityapps.shuttle.ui.screens.search

import android.content.Context
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecentSearchesTest {
    private lateinit var preferenceManager: GeneralPreferenceManager

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        preferenceManager = GeneralPreferenceManager(context.getSharedPreferences("recent-searches-test", Context.MODE_PRIVATE).apply { edit().clear().commit() })
    }

    @Test
    fun `adding puts the query first and drops an earlier copy of it`() {
        preferenceManager.recentSearches = listOf("air", "bjork")
        val recent = RecentSearches(preferenceManager)

        recent.add("  Bjork ")

        recent.searches.value shouldBe listOf("Bjork", "air")
        preferenceManager.recentSearches shouldBe listOf("Bjork", "air")
    }

    @Test
    fun `keeps only the newest ten`() {
        val recent = RecentSearches(preferenceManager)

        (1..12).forEach { recent.add("query $it") }

        recent.searches.value shouldBe (12 downTo 3).map { "query $it" }
    }

    @Test
    fun `ignores blank queries`() {
        val recent = RecentSearches(preferenceManager)

        recent.add("   ")

        recent.searches.value shouldBe emptyList()
    }
}
