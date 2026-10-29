package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.SystemSurfaceText
import com.simplecityapps.shuttle.model.songsView
import com.simplecityapps.shuttle.model.systemSurfaceText
import com.simplecityapps.shuttle.settings.AppearanceSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow

/** The text Now Playing and the widgets show for a queued song, with Classical display applied. */
@SingleIn(AppScope::class)
class IosSystemSurfaceText @Inject constructor(
    private val queueOperations: QueueOperations,
    private val appearanceSettings: AppearanceSettings
) {
    /** Emits the setting now and on each change, so Swift can refresh what it already published. */
    val classicalDisplay: Flow<Boolean> = appearanceSettings.classicalDisplay.flow

    /** Null when the queue no longer has [uid]. */
    fun text(uid: Long): SystemSurfaceText? {
        val items = queueOperations.queueStateFlow.value.items
        val index = items.indexOfFirst { it.uid == uid }
        if (index == -1) return null
        return systemSurfaceText(items.songsView { it.song }, index, appearanceSettings.classicalDisplay.value)
    }
}
