package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** The last [MaxRecentSearches] queries the user searched for, newest first, persisted across launches. */
@Singleton
class RecentSearches @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) {
    private val _searches = MutableStateFlow(preferenceManager.recentSearches)
    val searches: StateFlow<List<String>> = _searches.asStateFlow()

    /** Moves [query] to the front; a repeat of an earlier query (ignoring case) replaces it. */
    fun add(query: String) {
        val trimmed = query.replace('\n', ' ').trim()
        if (trimmed.isEmpty()) return
        save { searches -> (listOf(trimmed) + searches.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MaxRecentSearches) }
    }

    fun remove(query: String) = save { searches -> searches - query }

    private fun save(transform: (List<String>) -> List<String>) {
        preferenceManager.recentSearches = _searches.updateAndGet(transform)
    }

    companion object {
        const val MaxRecentSearches = 10
    }
}
