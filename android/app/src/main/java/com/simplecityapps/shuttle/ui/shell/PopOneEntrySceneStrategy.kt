package com.simplecityapps.shuttle.ui.shell

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Makes back from a scene [delegate] builds pop one entry. `NavDisplay` pops as many entries as separate the scene's
 * entries from its `previousEntries`, and a list-detail scene's previous entries sit below its list pane, so back from
 * a detail beside its list popped the list too and left the tab. Here the previous entries are the back stack less its
 * top entry, so back closes the detail and leaves the list showing.
 */
internal class PopOneEntrySceneStrategy<T : Any>(private val delegate: SceneStrategy<T>) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val scene = with(delegate) { calculateScene(entries) } ?: return null
        return object : Scene<T> by scene {
            override val previousEntries: List<NavEntry<T>> = entries.dropLast(1)
        }
    }
}
