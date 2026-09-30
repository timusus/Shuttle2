package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.ui.screens.search.LibrarySearchIndex
import dev.zacsweers.metro.Inject

/** Builds the search index once the library has songs, so the first search doesn't wait for it (#677). */
class SearchIndexInitializer
@Inject
constructor(
    private val libraryIndex: LibrarySearchIndex
) : AppInitializer {
    override fun init(application: Application) {
        libraryIndex.warmUp()
    }
}
