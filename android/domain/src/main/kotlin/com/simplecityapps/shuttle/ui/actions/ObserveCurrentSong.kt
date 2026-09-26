package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The song at the queue's current position, or null with an empty queue; emits only when it changes. */
class ObserveCurrentSong @Inject constructor(
    private val queueOperations: QueueOperations,
) {
    operator fun invoke(): Flow<Song?> = queueOperations.queueStateFlow
        .map { it.currentItem?.song }
        .distinctUntilChanged()
}
