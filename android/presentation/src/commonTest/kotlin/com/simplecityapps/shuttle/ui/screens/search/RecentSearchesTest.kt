package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RecentSearchesTest {
    private val preferenceManager = GeneralPreferenceManager(InMemoryKeyValueStore())

    @Test
    fun `adding puts the query first and drops an earlier copy of it`() {
        preferenceManager.recentSearches = listOf("salt", "kestrel")
        val recent = RecentSearches(preferenceManager)

        recent.add("  Kestrel ")

        recent.searches.value shouldBe listOf("Kestrel", "salt")
        preferenceManager.recentSearches shouldBe listOf("Kestrel", "salt")
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
